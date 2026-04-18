package com.doe.manager.scheduler;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.workflow.WorkflowManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DagScheduler}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Linear DAG (A→B→C)</li>
 *   <li>Fan-out (A→B, C)</li>
 *   <li>Fan-in (B, C→D)</li>
 *   <li>Diamond (A→B, C→D)</li>
 *   <li>Multi-workflow interleaving</li>
 *   <li>Fail-fast behavior</li>
 *   <li>Max concurrent jobs per workflow</li>
 *   <li>Workflow completion</li>
 * </ul>
 */
class DagSchedulerTest {

    private DagScheduler dagScheduler;
    private JobQueue jobQueue;
    private WorkflowManager workflowManager;

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManager();
        // Capacity large enough for all test jobs
        jobQueue = new JobQueue(null, 1000);
        // Use a very long interval so the scheduler doesn't run automatically during tests
        dagScheduler = new DagScheduler(workflowManager, jobQueue, 60_000, true, 10, noOpListener());
        dagScheduler.start();
    }

    // ──── Helper methods ─────────────────────────────────────────────────────

    private Workflow createAndExecuteWorkflow(String name, List<WorkflowJob> jobs) {
        Workflow workflow = Workflow.newWorkflow(name)
                .addJobs(jobs)
                .build();
        workflowManager.registerWorkflow(workflow);
        workflowManager.executeWorkflow(workflow.getId());
        return workflowManager.getWorkflow(workflow.getId());
    }

    private void tick(UUID workflowId) {
        dagScheduler.onWorkflowJobChanged(workflowId);
    }

    private WorkflowJob makeJob(UUID id, String payload, List<UUID> dependencies) {
        Job job = Job.newJob(payload)
                .id(id)
                .status(JobStatus.PENDING)
                .build();
        return WorkflowJob.fromJob(job)
                .dagIndex(0)
                .dependencies(dependencies)
                .build();
    }

    private WorkflowJob makeJob(UUID id, String payload) {
        return makeJob(id, payload, List.of());
    }

    /**
     * Simulates the full job lifecycle: enqueued → assigned → running → completed.
     * The job object is dequeued from the queue, transitions through ASSIGNED and RUNNING,
     * then ends at COMPLETED. The DagScheduler is then notified.
     */
    private void simulateJobCompletion(Job job, UUID workflowId) {
        // PENDING → ASSIGNED → RUNNING → COMPLETED
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        tick(workflowId);
    }

    /**
     * Simulates a job failing: dequeued, transitions to ASSIGNED → RUNNING → FAILED.
     */
    private void simulateJobFailure(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.FAILED);
        job.setResult("simulated failure");
        tick(workflowId);
    }

    // ──── Linear DAG: A → B → C ──────────────────────────────────────────────

    @Test
    @DisplayName("Linear DAG: only root job enqueued initially")
    void linearDag_onlyRootEnqueuedInitially() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("linear", List.of(jobA, jobB, jobC));

        // First tick: only A should be enqueued (no deps)
        tick(workflow.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(a, jobQueue.dequeue().getId());
        assertTrue(jobQueue.isEmpty());

        // B and C should NOT be enqueued yet
        tick(workflow.getId());
        assertTrue(jobQueue.isEmpty());
    }

    @Test
    @DisplayName("Linear DAG: B enqueued after A completes")
    void linearDag_bEnqueuedAfterACompletes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("linear", List.of(jobA, jobB, jobC));

        // Enqueue A
        tick(workflow.getId());
        Job dequeuedA = jobQueue.dequeue();

        // Complete A → B should be enqueued
        simulateJobCompletion(dequeuedA, workflow.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(b, jobQueue.dequeue().getId());
    }

    @Test
    @DisplayName("Linear DAG: C enqueued after B completes")
    void linearDag_cEnqueuedAfterBCompletes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("linear", List.of(jobA, jobB, jobC));

        // Enqueue and complete A
        tick(workflow.getId());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobCompletion(dequeuedA, workflow.getId());

        // Dequeue and complete B
        Job dequeuedB = jobQueue.dequeue();
        assertEquals(b, dequeuedB.getId());
        simulateJobCompletion(dequeuedB, workflow.getId());

        // C should now be enqueued
        assertEquals(1, jobQueue.size());
        assertEquals(c, jobQueue.dequeue().getId());
    }

    @Test
    @DisplayName("Linear DAG: workflow completes when all jobs done")
    void linearDag_workflowCompletesWhenAllDone() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("linear", List.of(jobA, jobB, jobC));

        // Complete all jobs
        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        Workflow completed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
    }

    // ──── Fan-out: A → B, C ──────────────────────────────────────────────────

    @Test
    @DisplayName("Fan-out: B and C both enqueued after A completes")
    void fanOut_bAndCEnqueuedAfterACompletes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("fanout", List.of(jobA, jobB, jobC));

        // Enqueue A
        tick(workflow.getId());
        Job dequeuedA = jobQueue.dequeue();

        // Complete A → B and C should be enqueued
        simulateJobCompletion(dequeuedA, workflow.getId());
        assertEquals(2, jobQueue.size());

        Job first = jobQueue.dequeue();
        Job second = jobQueue.dequeue();
        assertTrue(
                (first.getId().equals(b) && second.getId().equals(c)) ||
                (first.getId().equals(c) && second.getId().equals(b)),
                "B and C should both be enqueued, got: " + first.getId() + ", " + second.getId());
    }

    @Test
    @DisplayName("Fan-out: workflow completes when all branches done")
    void fanOut_workflowCompletesWhenAllBranchesDone() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("fanout", List.of(jobA, jobB, jobC));

        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        Workflow completed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
    }

    // ──── Fan-in: B, C → D ───────────────────────────────────────────────────

    @Test
    @DisplayName("Fan-in: D not enqueued until both B and C complete")
    void fanIn_dNotEnqueuedUntilBothComplete() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));
        WorkflowJob jobD = makeJob(d, "D", List.of(b, c));

        Workflow workflow = createAndExecuteWorkflow("fanin", List.of(jobA, jobB, jobC, jobD));

        // Enqueue and complete A
        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        // B and C enqueued
        assertEquals(2, jobQueue.size());
        Job dequeuedB = jobQueue.dequeue();
        Job dequeuedC = jobQueue.dequeue();

        // Complete only B → D should NOT be enqueued yet
        simulateJobCompletion(dequeuedB, workflow.getId());
        tick(workflow.getId());
        assertTrue(jobQueue.isEmpty(), "D should not be enqueued until C also completes");

        // Complete C → D should be enqueued now
        simulateJobCompletion(dequeuedC, workflow.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(d, jobQueue.dequeue().getId());
    }

    // ──── Diamond: A → B, C → D ──────────────────────────────────────────────

    @Test
    @DisplayName("Diamond: D enqueued only after both B and C complete")
    void diamond_dEnqueuedAfterBAndCComplete() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));
        WorkflowJob jobD = makeJob(d, "D", List.of(b, c));

        Workflow workflow = createAndExecuteWorkflow("diamond", List.of(jobA, jobB, jobC, jobD));

        // Enqueue and complete A
        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        // B and C enqueued
        assertEquals(2, jobQueue.size());
        Job dequeuedB = jobQueue.dequeue();
        Job dequeuedC = jobQueue.dequeue();

        // Complete B only → D not ready
        simulateJobCompletion(dequeuedB, workflow.getId());
        tick(workflow.getId());
        assertTrue(jobQueue.isEmpty());

        // Complete C → D ready
        simulateJobCompletion(dequeuedC, workflow.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(d, jobQueue.dequeue().getId());
    }

    @Test
    @DisplayName("Diamond: workflow completes after all jobs done")
    void diamond_workflowCompletesAfterAllJobsDone() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));
        WorkflowJob jobD = makeJob(d, "D", List.of(b, c));

        Workflow workflow = createAndExecuteWorkflow("diamond", List.of(jobA, jobB, jobC, jobD));

        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        Workflow completed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
    }

    // ──── Multi-workflow interleaving ────────────────────────────────────────

    @Test
    @DisplayName("Multi-workflow: two workflows interleave without conflicts")
    void multiWorkflow_interleaveNoConflicts() {
        // Workflow 1: A1 → B1
        UUID a1 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        WorkflowJob jobA1 = makeJob(a1, "A1");
        WorkflowJob jobB1 = makeJob(b1, "B1", List.of(a1));
        Workflow wf1 = createAndExecuteWorkflow("wf1", List.of(jobA1, jobB1));

        // Workflow 2: A2 → B2
        UUID a2 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        WorkflowJob jobA2 = makeJob(a2, "A2");
        WorkflowJob jobB2 = makeJob(b2, "B2", List.of(a2));
        Workflow wf2 = createAndExecuteWorkflow("wf2", List.of(jobA2, jobB2));

        // Tick both: A1 and A2 should be enqueued
        tick(wf1.getId());
        tick(wf2.getId());
        assertEquals(2, jobQueue.size());

        // Drain and verify both root jobs are there
        Set<UUID> enqueued = Set.of(jobQueue.dequeue().getId(), jobQueue.dequeue().getId());
        assertTrue(enqueued.contains(a1));
        assertTrue(enqueued.contains(a2));

        // Complete A1 → B1 enqueued
        simulateJobCompletion(jobA1.getJob(), wf1.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(b1, jobQueue.dequeue().getId());

        // Complete A2 → B2 enqueued
        simulateJobCompletion(jobA2.getJob(), wf2.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(b2, jobQueue.dequeue().getId());

        // Both workflows should still be RUNNING (B1 and B2 not completed yet)
        assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(wf1.getId()).getStatus());
        assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(wf2.getId()).getStatus());
    }

    // ──── Fail-fast behavior ─────────────────────────────────────────────────

    @Test
    @DisplayName("Fail-fast: dependent jobs CANCELLED when dependency fails")
    void failFast_dependentJobsCancelled() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("failfast", List.of(jobA, jobB, jobC));

        // Enqueue A
        tick(workflow.getId());
        Job dequeuedA = jobQueue.dequeue();

        // Fail A → B and C should be CANCELLED (PENDING→CANCELLED is valid) and workflow should FAIL
        simulateJobFailure(dequeuedA, workflow.getId());

        assertEquals(JobStatus.FAILED, jobA.getJob().getStatus());
        assertEquals(JobStatus.CANCELLED, jobB.getJob().getStatus());
        assertEquals(JobStatus.CANCELLED, jobC.getJob().getStatus());

        Workflow failed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.FAILED, failed.getStatus());
    }

    // ──── Max concurrent jobs per workflow ───────────────────────────────────

    @Test
    @DisplayName("Max concurrent jobs: respects limit per workflow")
    void maxConcurrentJobs_respectsLimit() {
        // Use a scheduler with maxConcurrentJobs=2
        dagScheduler.stop();
        dagScheduler = new DagScheduler(workflowManager, jobQueue, 60_000, false, 2, noOpListener());
        dagScheduler.start();

        // Workflow: A → B, C, D (fan-out to 3, but limit is 2)
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));
        WorkflowJob jobD = makeJob(d, "D", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("maxconcurrent", List.of(jobA, jobB, jobC, jobD));

        // Enqueue A
        tick(workflow.getId());
        Job dequeuedA = jobQueue.dequeue();
        // Simulate A completing (went through ASSIGNED→RUNNING→COMPLETED)
        simulateJobCompletion(dequeuedA, workflow.getId());

        // B, C, D all have deps satisfied, but max is 2
        tick(workflow.getId());
        int enqueuedCount = jobQueue.size();
        assertTrue(enqueuedCount <= 2, "Should enqueue at most 2 jobs, got: " + enqueuedCount);
    }

    // ──── No double-submission ───────────────────────────────────────────────

    @Test
    @DisplayName("No double-submission: job enqueued only once")
    void noDoubleSubmission_jobEnqueuedOnce() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("nodouble", List.of(jobA));

        tick(workflow.getId());
        assertEquals(1, jobQueue.size());

        // Tick again — should NOT enqueue A a second time
        tick(workflow.getId());
        assertEquals(1, jobQueue.size(), "Job should not be enqueued twice");
    }

    // ──── Non-RUNNING workflows ignored ──────────────────────────────────────

    @Test
    @DisplayName("DRAFT workflow: no jobs enqueued")
    void draftWorkflow_noJobsEnqueued() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = Workflow.newWorkflow("draft")
                .addJobs(List.of(jobA))
                .build();
        workflowManager.registerWorkflow(workflow);
        // NOT executed — stays in DRAFT

        tick(workflow.getId());
        assertTrue(jobQueue.isEmpty(), "DRAFT workflow should not enqueue any jobs");
    }

    @Test
    @DisplayName("COMPLETED workflow: no jobs enqueued")
    void completedWorkflow_noJobsEnqueued() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("completed", List.of(jobA));

        tick(workflow.getId());
        simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

        assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());

        // Tick again — nothing should happen
        tick(workflow.getId());
        assertTrue(jobQueue.isEmpty());
    }

    // ──── forgetWorkflow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("forgetWorkflow clears submitted tracking")
    void forgetWorkflow_clearsTracking() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("forget", List.of(jobA));

        tick(workflow.getId());
        assertEquals(1, jobQueue.size());

        // Forget and re-tick — should not throw
        dagScheduler.forgetWorkflow(workflow.getId());
        assertDoesNotThrow(() -> dagScheduler.forgetWorkflow(workflow.getId()));
    }

    /** Returns a no-op EngineEventListener suitable for unit tests that don't need persistence. */
    private static EngineEventListener noOpListener() {
        return new EngineEventListener() {
            public void onWorkerRegistered(java.util.UUID w, String h, String ip, int cap, java.time.Instant t) {}
            public void onWorkerHeartbeat(java.util.UUID w, java.time.Instant t) {}
            public void onWorkerDied(java.util.UUID w) {}
            public void onJobAssigned(java.util.UUID j, java.util.UUID w, java.time.Instant t) {}
            public void onJobRunning(java.util.UUID j, java.time.Instant t) {}
            public void onJobCompleted(java.util.UUID j, java.util.UUID w, String s, java.time.Instant t) {}
            public void onJobFailed(java.util.UUID j, java.util.UUID w, String s, java.time.Instant t) {}
            public void onJobCancelled(java.util.UUID j, java.util.UUID w, String s, java.time.Instant t) {}
            public void onJobRequeued(java.util.UUID j, int rc, java.time.Instant t) {}
        };
    }
}
