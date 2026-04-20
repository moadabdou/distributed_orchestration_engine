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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobResultListener}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>onJobCompleted triggers DAG re-evaluation</li>
 *   <li>onJobFailed triggers DAG re-evaluation with fail-fast</li>
 *   <li>Job-to-workflow reverse index is built lazily</li>
 *   <li>Non-workflow jobs are ignored</li>
 *   <li>Workflow reaches terminal state after all jobs complete/fail</li>
 * </ul>
 */
class JobResultListenerTest {

    private JobResultListener listener;
    private DagScheduler dagScheduler;
    private WorkflowManager workflowManager;
    private JobQueue jobQueue;

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManager();
        jobQueue = new JobQueue(null, 1000);
        dagScheduler = new DagScheduler(workflowManager, jobQueue, 60_000, true, 10, noOpListener());
        listener = new JobResultListener(workflowManager, dagScheduler);
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

    private WorkflowJob makeJob(UUID id, String payload, List<UUID> dependencies) {
        Job job = Job.newJob(payload)
                .id(id)
                .status(JobStatus.PENDING)
                .timeoutMs(60000L)
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
     * Simulates a job completing via the real flow:
     * transition through ASSIGNED → RUNNING → COMPLETED, then fire the listener event.
     */
    private void simulateJobComplete(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        listener.onJobCompleted(job.getId(), null, "success", Instant.now());
    }

    /**
     * Simulates a job failing via the real flow:
     * transition through ASSIGNED → RUNNING → FAILED, then fire the listener event.
     */
    private void simulateJobFailed(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.FAILED);
        job.setResult("error");
        listener.onJobFailed(job.getId(), null, "error", Instant.now());
    }

    /**
     * Simulates a job being cancelled via the real flow.
     */
    private void simulateJobCancelled(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.CANCELLED);
        job.setResult("user cancelled");
        listener.onJobCancelled(job.getId(), null, "user cancelled", Instant.now());
    }

    private void tick(UUID workflowId) {
        dagScheduler.onWorkflowJobChanged(workflowId);
    }

    // ──── onJobCompleted ─────────────────────────────────────────────────────

    @Test
    @DisplayName("onJobCompleted triggers re-evaluation and enqueues dependent jobs")
    void onJobCompleted_enqueuesDependentJobs() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("test-completed", List.of(jobA, jobB));

        // Manually enqueue A (as DagScheduler would)
        jobQueue.enqueue(jobA.getJob());

        // Dequeue A and simulate completion via the listener
        Job dequeuedA = jobQueue.dequeue();
        simulateJobComplete(dequeuedA, workflow.getId());

        // B should now be enqueued
        assertEquals(1, jobQueue.size(), "Job B should be enqueued after A completes");
        assertEquals(b, jobQueue.dequeue().getId());
    }

    @Test
    @DisplayName("onJobCompleted completes workflow when all jobs done")
    void onJobCompleted_completesWorkflow() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("test-complete-wf", List.of(jobA, jobB));

        // Simulate: A enqueued, dequeued, completed
        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobComplete(dequeuedA, workflow.getId());

        // B enqueued, dequeued, completed
        assertEquals(1, jobQueue.size());
        Job dequeuedB = jobQueue.dequeue();
        simulateJobComplete(dequeuedB, workflow.getId());

        // Workflow should be COMPLETED
        Workflow completed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
    }

    // ──── onJobFailed ────────────────────────────────────────────────────────

    @Test
    @DisplayName("onJobFailed triggers fail-fast for dependent jobs")
    void onJobFailed_failFast() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));

        Workflow workflow = createAndExecuteWorkflow("test-failfast", List.of(jobA, jobB, jobC));

        // Simulate: A enqueued, dequeued, failed
        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobFailed(dequeuedA, workflow.getId());

        // A should be FAILED, B and C should be SKIPPED (as workflow is now terminal)
        assertEquals(JobStatus.FAILED, jobA.getJob().getStatus());
        assertEquals(JobStatus.SKIPPED, jobB.getJob().getStatus());
        assertEquals(JobStatus.SKIPPED, jobC.getJob().getStatus());

        // Workflow should be FAILED (since A failed and B/C are blocked)
        Workflow failed = workflowManager.getWorkflow(workflow.getId());
        assertEquals(WorkflowStatus.FAILED, failed.getStatus());
    }

    @Test
    @DisplayName("onJobFailed with no dependents fails workflow")
    void onJobFailed_noDependents_failsWorkflow() {
        UUID a = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");

        Workflow workflow = createAndExecuteWorkflow("test-fail-single", List.of(jobA));

        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobFailed(dequeuedA, workflow.getId());

        assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());
    }

    // ──── Reverse index ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Reverse index maps jobId to workflowId")
    void reverseIndex_mapsJobIdToWorkflowId() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("test-index", List.of(jobA));

        // Trigger the listener to build the index
        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobComplete(dequeuedA, workflow.getId());

        assertEquals(workflow.getId(), listener.getJobToWorkflowIndex().get(a));
    }

    @Test
    @DisplayName("Jobs not in any workflow are ignored")
    void nonWorkflowJob_ignored() {
        UUID unknownJob = UUID.randomUUID();

        // No workflow registered for this job
        listener.onJobCompleted(unknownJob, null, "ok", Instant.now());

        // Should not throw, and queue should be empty
        assertTrue(jobQueue.isEmpty());
    }

    // ──── onJobCancelled ─────────────────────────────────────────────────────

    @Test
    @DisplayName("onJobCancelled triggers re-evaluation similar to failure")
    void onJobCancelled_triggersReEvaluation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));

        Workflow workflow = createAndExecuteWorkflow("test-cancel", List.of(jobA, jobB));

        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobCancelled(dequeuedA, workflow.getId());

        // B should be SKIPPED (as workflow is now terminal)
        assertEquals(JobStatus.SKIPPED, jobB.getJob().getStatus());
        // Workflow should fail because A cancelled and B is blocked
        assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());
    }

    // ──── onJobRequeued ──────────────────────────────────────────────────────

    @Test
    @DisplayName("onJobRequeued is a no-op — CrashRecoveryHandler handles requeue directly")
    void onJobRequeued_isNoOp() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("test-requeue", List.of(jobA));

        tick(workflow.getId());
        assertEquals(1, jobQueue.size());

        // Simulate requeue event — should not throw, should not change tracking
        listener.onJobRequeued(a, 1, Instant.now());
        assertEquals(1, jobQueue.size());
    }

    @Test
    @DisplayName("onJobRequeued with unknown jobId does not throw")
    void onJobRequeued_unknownJob_doesNotThrow() {
        UUID unknownJob = UUID.randomUUID();
        assertDoesNotThrow(() -> listener.onJobRequeued(unknownJob, 1, Instant.now()));
    }

    // ──── forgetWorkflow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("forgetWorkflow clears reverse index entries")
    void forgetWorkflow_clearsReverseIndex() {
        UUID a = UUID.randomUUID();
        WorkflowJob jobA = makeJob(a, "A");
        Workflow workflow = createAndExecuteWorkflow("test-forget", List.of(jobA));

        // Build the index
        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobComplete(dequeuedA, workflow.getId());

        assertNotNull(listener.getJobToWorkflowIndex().get(a));

        // Forget
        listener.forgetWorkflow(workflow.getId());
        assertNull(listener.getJobToWorkflowIndex().get(a));
    }

    // ──── Multi-workflow reverse index ───────────────────────────────────────

    @Test
    @DisplayName("Reverse index handles multiple workflows correctly")
    void reverseIndex_multiWorkflow() {
        // Workflow 1: A1
        UUID a1 = UUID.randomUUID();
        WorkflowJob jobA1 = makeJob(a1, "A1");
        Workflow wf1 = createAndExecuteWorkflow("wf1", List.of(jobA1));

        // Workflow 2: A2
        UUID a2 = UUID.randomUUID();
        WorkflowJob jobA2 = makeJob(a2, "A2");
        Workflow wf2 = createAndExecuteWorkflow("wf2", List.of(jobA2));

        // Build index for both
        jobQueue.enqueue(jobA1.getJob());
        Job dequeuedA1 = jobQueue.dequeue();
        simulateJobComplete(dequeuedA1, wf1.getId());

        jobQueue.enqueue(jobA2.getJob());
        Job dequeuedA2 = jobQueue.dequeue();
        simulateJobComplete(dequeuedA2, wf2.getId());

        assertEquals(wf1.getId(), listener.getJobToWorkflowIndex().get(a1));
        assertEquals(wf2.getId(), listener.getJobToWorkflowIndex().get(a2));
    }

    // ──── Fan-in completion via listener ─────────────────────────────────────

    @Test
    @DisplayName("Fan-in: listener triggers correct dependency resolution")
    void fanIn_listenerResolvesDependencies() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(a));
        WorkflowJob jobD = makeJob(d, "D", List.of(b, c));

        Workflow workflow = createAndExecuteWorkflow("fanin-listener", List.of(jobA, jobB, jobC, jobD));

        // A enqueued, dequeued, completed
        jobQueue.enqueue(jobA.getJob());
        Job dequeuedA = jobQueue.dequeue();
        simulateJobComplete(dequeuedA, workflow.getId());

        // B and C enqueued
        assertEquals(2, jobQueue.size());
        Job dequeuedB = jobQueue.dequeue();
        Job dequeuedC = jobQueue.dequeue();

        // Complete B via listener → D NOT enqueued yet
        simulateJobComplete(dequeuedB, workflow.getId());
        assertEquals(0, jobQueue.size());

        // Complete C via listener → D enqueued
        simulateJobComplete(dequeuedC, workflow.getId());
        assertEquals(1, jobQueue.size());
        assertEquals(d, jobQueue.dequeue().getId());
    }

    @Test
    @DisplayName("Complex DAG with failure: workflow fails when all reachable jobs finish")
    void complexDag_withFailure_failsWorkflowWhenBlocked() {
        // A -> B -> C
        //   -> D (independent fail)
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        WorkflowJob jobA = makeJob(a, "A");
        WorkflowJob jobB = makeJob(b, "B", List.of(a));
        WorkflowJob jobC = makeJob(c, "C", List.of(b));
        WorkflowJob jobD = makeJob(d, "D"); // No dependencies

        Workflow workflow = createAndExecuteWorkflow("complex-fail", List.of(jobA, jobB, jobC, jobD));

        // Enqueue A and D
        jobQueue.enqueue(jobA.getJob());
        jobQueue.enqueue(jobD.getJob());

        // Fail A
        simulateJobFailed(jobQueue.dequeue(), workflow.getId());
        // Workflow still RUNNING because D is enqueued (pending execution)
        assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());

        // Complete D
        simulateJobComplete(jobQueue.dequeue(), workflow.getId());

        // Now A is FAILED, B is SKIPPED (blocked), C is SKIPPED (blocked), D is COMPLETED.
        // The workflow should transition to FAILED.
        assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        assertEquals(JobStatus.FAILED, jobA.getJob().getStatus());
        assertEquals(JobStatus.SKIPPED, jobB.getJob().getStatus());
        assertEquals(JobStatus.SKIPPED, jobC.getJob().getStatus());
        assertEquals(JobStatus.COMPLETED, jobD.getJob().getStatus());
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
            public void onJobSkipped(java.util.UUID j, java.time.Instant t) {}
        };
    }
}
