package com.doe.manager;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.scheduler.DagScheduler;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.scheduler.JobResultListener;
import com.doe.manager.server.TestManagerServerBuilder;
import com.doe.manager.workflow.WorkflowManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Integration Tests — Happy Paths, Edge Cases, and Pressure Tests.
 *
 * <p>These tests verify the complete in-memory workflow engine works correctly
 * as an integrated system, covering:
 * <ul>
 *   <li>Happy path workflows with various DAG topologies</li>
 *   <li>Edge cases: empty workflows, single jobs, deep chains, wide fan-outs</li>
 *   <li>Pressure tests: concurrent workflow registration, high job volume, rapid state transitions</li>
 *   <li>System stability under stress: no deadlocks, no data corruption, correct final states</li>
 * </ul>
 *
 * <p>All tests are pure in-memory — no DB, no HTTP, no external dependencies.
 */
class Phase1IntegrationTest {

    private WorkflowManager workflowManager;
    private JobQueue jobQueue;
    private DagScheduler dagScheduler;
    private JobResultListener jobResultListener;

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManager();
        jobQueue = new JobQueue(null, 10000); // Large capacity for pressure tests
        dagScheduler = new DagScheduler(workflowManager, jobQueue, 60_000, true, 10, TestManagerServerBuilder.NO_OP_LISTENER);
        jobResultListener = new JobResultListener(workflowManager, dagScheduler);
        dagScheduler.start();
    }

    // ──── Helpers ───────────────────────────────────────────────────────────

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

    private WorkflowJob makeJob(String payload) {
        return makeJob(UUID.randomUUID(), payload, List.of());
    }

    private Workflow registerAndExecute(String name, List<WorkflowJob> jobs) {
        Workflow workflow = Workflow.newWorkflow(name)
                .addJobs(jobs)
                .build();
        workflowManager.registerWorkflow(workflow);
        return workflowManager.executeWorkflow(workflow.getId());
    }

    private void tick(UUID workflowId) {
        dagScheduler.onWorkflowJobChanged(workflowId);
    }

    private void simulateJobCompletion(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        jobResultListener.onJobCompleted(job.getId(), null, "success", java.time.Instant.now());
    }

    private void simulateJobFailure(Job job, UUID workflowId) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.FAILED);
        job.setResult("error");
        jobResultListener.onJobFailed(job.getId(), null, "error", java.time.Instant.now());
    }


    // =========================================================================
    // HAPPY PATH TESTS
    // =========================================================================

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("single job workflow completes successfully")
        void singleJobWorkflow() {
            WorkflowJob job = makeJob("single-task");
            Workflow workflow = registerAndExecute("single-job", List.of(job));

            tick(workflow.getId());
            assertEquals(1, jobQueue.size());

            Job dequeued = jobQueue.dequeue();
            simulateJobCompletion(dequeued, workflow.getId());

            Workflow completed = workflowManager.getWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(JobStatus.COMPLETED, completed.getJob(job.getJob().getId()).getJob().getStatus());
        }

        @Test
        @DisplayName("linear chain: A → B → C → D completes in order")
        void linearChainCompletes() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();

            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of(a));
            WorkflowJob jobC = makeJob(c, "C", List.of(b));
            WorkflowJob jobD = makeJob(d, "D", List.of(c));

            Workflow workflow = registerAndExecute("linear-chain", List.of(jobA, jobB, jobC, jobD));

            // Execute the chain
            List<UUID> executionOrder = new ArrayList<>();

            tick(workflow.getId());
            executionOrder.add(jobQueue.dequeue().getId());
            simulateJobCompletion(jobA.getJob(), workflow.getId());

            tick(workflow.getId());
            executionOrder.add(jobQueue.dequeue().getId());
            simulateJobCompletion(jobB.getJob(), workflow.getId());

            tick(workflow.getId());
            executionOrder.add(jobQueue.dequeue().getId());
            simulateJobCompletion(jobC.getJob(), workflow.getId());

            tick(workflow.getId());
            executionOrder.add(jobQueue.dequeue().getId());
            simulateJobCompletion(jobD.getJob(), workflow.getId());

            // Verify execution order
            assertEquals(List.of(a, b, c, d), executionOrder);

            Workflow completed = workflowManager.getWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
        }

        @Test
        @DisplayName("diamond pattern: A → B,C → D completes correctly")
        void diamondPatternCompletes() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();

            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of(a));
            WorkflowJob jobC = makeJob(c, "C", List.of(a));
            WorkflowJob jobD = makeJob(d, "D", List.of(b, c));

            Workflow workflow = registerAndExecute("diamond", List.of(jobA, jobB, jobC, jobD));

            // A executes first
            tick(workflow.getId());
            assertEquals(1, jobQueue.size());
            Job dequeuedA = jobQueue.dequeue();
            simulateJobCompletion(dequeuedA, workflow.getId());

            // B and C execute in parallel (both enqueued)
            tick(workflow.getId());
            System.out.println("DEBUG diamondPattern: Queue Size = " + jobQueue.size());
            assertEquals(2, jobQueue.size());
            Job dequeuedB = jobQueue.dequeue();
            Job dequeuedC = jobQueue.dequeue();

            // Complete B first - D should NOT be enqueued yet
            simulateJobCompletion(dequeuedB, workflow.getId());
            assertEquals(0, jobQueue.size(), "D should not be enqueued until C completes");

            // Complete C - D should now be enqueued
            simulateJobCompletion(dequeuedC, workflow.getId());
            assertEquals(1, jobQueue.size());
            Job dequeuedD = jobQueue.dequeue();
            assertEquals(d, dequeuedD.getId());

            // Complete D
            simulateJobCompletion(dequeuedD, workflow.getId());

            Workflow completed = workflowManager.getWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
            assertEquals(JobStatus.COMPLETED, jobA.getJob().getStatus());
            assertEquals(JobStatus.COMPLETED, jobB.getJob().getStatus());
            assertEquals(JobStatus.COMPLETED, jobC.getJob().getStatus());
            assertEquals(JobStatus.COMPLETED, jobD.getJob().getStatus());
        }

        @Test
        @DisplayName("fan-out then fan-in: A → B,C,D → E")
        void fanOutFanIn() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();
            UUID e = UUID.randomUUID();

            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of(a));
            WorkflowJob jobC = makeJob(c, "C", List.of(a));
            WorkflowJob jobD = makeJob(d, "D", List.of(a));
            WorkflowJob jobE = makeJob(e, "E", List.of(b, c, d));

            Workflow workflow = registerAndExecute("fanout-fanin", List.of(jobA, jobB, jobC, jobD, jobE));

            // A executes
            tick(workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // B, C, D execute
            tick(workflow.getId());
            assertEquals(3, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // E executes
            tick(workflow.getId());
            assertEquals(1, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            Workflow completed = workflowManager.getWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
        }

        @Test
        @DisplayName("multiple independent workflows execute without interference")
        void multipleIndependentWorkflows() {
            // Workflow 1: A1 → B1
            UUID a1 = UUID.randomUUID();
            UUID b1 = UUID.randomUUID();
            WorkflowJob jobA1 = makeJob(a1, "A1", List.of());
            WorkflowJob jobB1 = makeJob(b1, "B1", List.of(a1));
            Workflow wf1 = registerAndExecute("wf1", List.of(jobA1, jobB1));

            // Workflow 2: A2 → B2
            UUID a2 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();
            WorkflowJob jobA2 = makeJob(a2, "A2", List.of());
            WorkflowJob jobB2 = makeJob(b2, "B2", List.of(a2));
            Workflow wf2 = registerAndExecute("wf2", List.of(jobA2, jobB2));

            // Execute both
            tick(wf1.getId());
            tick(wf2.getId());
            assertEquals(2, jobQueue.size());

            // We must identify which job belongs to which workflow
            Job j1 = jobQueue.dequeue();
            Job j2 = jobQueue.dequeue();
            Job root1 = j1.getPayload().equals("A1") ? j1 : j2;
            Job root2 = j1.getPayload().equals("A1") ? j2 : j1;

            // Complete workflow 1
            simulateJobCompletion(root1, wf1.getId());
            
            Job leaf1 = jobQueue.dequeue();
            simulateJobCompletion(leaf1, wf1.getId());
            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(wf1.getId()).getStatus());

            // Workflow 2 should still be RUNNING
            assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(wf2.getId()).getStatus());

            // Complete workflow 2
            tick(wf2.getId()); // No-op, A2 already transitioned or waiting? Wait, A2 is in our hands.
            simulateJobCompletion(root2, wf2.getId());
            Job leaf2 = jobQueue.dequeue();
            simulateJobCompletion(leaf2, wf2.getId());
            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(wf2.getId()).getStatus());
        }

        @Test
        @DisplayName("workflow with multiple root nodes executes correctly")
        void multipleRootNodes() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();

            // A, B, C are roots; D depends on all
            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of());
            WorkflowJob jobC = makeJob(c, "C", List.of());
            WorkflowJob jobD = makeJob(d, "D", List.of(a, b, c));

            Workflow workflow = registerAndExecute("multi-root", List.of(jobA, jobB, jobC, jobD));

            // All roots should be enqueued
            tick(workflow.getId());
            assertEquals(3, jobQueue.size());

            // Complete all roots
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // D should be enqueued
            tick(workflow.getId());
            assertEquals(1, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }
    }

    // =========================================================================
    // EDGE CASE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty workflow completes immediately")
        void emptyWorkflow() {
            Workflow workflow = Workflow.newWorkflow("empty").build();
            workflowManager.registerWorkflow(workflow);
            workflowManager.executeWorkflow(workflow.getId());

            tick(workflow.getId());
            assertEquals(0, jobQueue.size());

            // Should complete immediately (no jobs to execute)
            Workflow completed = workflowManager.getWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.COMPLETED, completed.getStatus());
        }

        @Test
        @DisplayName("deep chain (10 levels) executes correctly")
        void deepChain10Levels() {
            int depth = 10;
            List<WorkflowJob> jobs = new ArrayList<>();
            List<UUID> ids = new ArrayList<>();

            for (int i = 0; i < depth; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                List<UUID> deps = i == 0 ? List.of() : List.of(ids.get(i - 1));
                jobs.add(makeJob(id, "job-" + i, deps));
            }

            Workflow workflow = registerAndExecute("deep-chain", jobs);

            // Execute the chain level by level
            for (int i = 0; i < depth; i++) {
                tick(workflow.getId());
                assertEquals(1, jobQueue.size(), "Level " + i + " should have 1 job");
                Job dequeued = jobQueue.dequeue();
                simulateJobCompletion(dequeued, workflow.getId());
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("wide fan-out (20 parallel jobs) executes correctly")
        void wideFanOut20Jobs() {
            UUID root = UUID.randomUUID();
            WorkflowJob rootJob = makeJob(root, "root", List.of());

            List<WorkflowJob> jobs = new ArrayList<>();
            jobs.add(rootJob);

            for (int i = 0; i < 20; i++) {
                UUID id = UUID.randomUUID();
                jobs.add(makeJob(id, "leaf-" + i, List.of(root)));
            }

            Workflow workflow = registerAndExecute("wide-fanout", jobs);

            // Execute root
            tick(workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // All 20 leaves should be enqueued (up to max concurrency 10)
            tick(workflow.getId());
            assertEquals(10, jobQueue.size());

            // Complete all leaves
            for (int i = 0; i < 20; i++) {
                Job leaf = jobQueue.dequeue();
                simulateJobCompletion(leaf, workflow.getId());
                tick(workflow.getId()); // Enqueue remaining leaves
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("complex DAG with 15 jobs and multiple paths")
        void complexDag15Jobs() {
            // Structure:
            //       A   B
            //      / \ / \
            //     C   D   E
            //    / \ / \ /
            //   F   G   H
            //    \  |  /
            //       I
            //      / \
            //     J   K
            //      \ /
            //       L
            //      / \
            //     M   N

            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID d = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID e = UUID.randomUUID();
            UUID f = UUID.randomUUID();
            UUID g = UUID.randomUUID();
            UUID h = UUID.randomUUID();
            UUID i = UUID.randomUUID();
            UUID j = UUID.randomUUID();
            UUID k = UUID.randomUUID();
            UUID l = UUID.randomUUID();
            UUID m = UUID.randomUUID();
            UUID n = UUID.randomUUID();

            List<WorkflowJob> jobs = List.of(
                    makeJob(a, "A", List.of()),
                    makeJob(b, "B", List.of()),
                    makeJob(c, "C", List.of(a)),
                    makeJob(d, "D", List.of(a, b)),
                    makeJob(e, "E", List.of(b)),
                    makeJob(f, "F", List.of(c)),
                    makeJob(g, "G", List.of(c, d)),
                    makeJob(h, "H", List.of(d, e)),
                    makeJob(i, "I", List.of(f, g, h)),
                    makeJob(j, "J", List.of(i)),
                    makeJob(k, "K", List.of(i)),
                    makeJob(l, "L", List.of(j, k)),
                    makeJob(m, "M", List.of(l)),
                    makeJob(n, "N", List.of(l))
            );

            Workflow workflow = registerAndExecute("complex-dag", jobs);

            // Layer 1: A, B
            tick(workflow.getId());
            assertEquals(2, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // Layer 2: C, D, E
            tick(workflow.getId());
            assertEquals(3, jobQueue.size());
            for (int idx = 0; idx < 3; idx++) {
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            // Layer 3: F, G, H
            tick(workflow.getId());
            assertEquals(3, jobQueue.size());
            for (int idx = 0; idx < 3; idx++) {
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            // Layer 4: I
            tick(workflow.getId());
            assertEquals(1, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // Layer 5: J, K
            tick(workflow.getId());
            assertEquals(2, jobQueue.size());
            for (int idx = 0; idx < 2; idx++) {
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            // Layer 6: L
            tick(workflow.getId());
            assertEquals(1, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // Layer 7: M, N
            tick(workflow.getId());
            assertEquals(2, jobQueue.size());
            for (int idx = 0; idx < 2; idx++) {
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("pause and resume during execution maintains correct state")
        void pauseResumeDuringExecution() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of(a));

            Workflow workflow = registerAndExecute("pause-resume", List.of(jobA, jobB));

            // Execute A
            tick(workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // Pause workflow
            workflowManager.pauseWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.PAUSED, workflowManager.getWorkflow(workflow.getId()).getStatus());

            // B should be enqueued but workflow is paused
            tick(workflow.getId());

            // Resume workflow
            workflowManager.resumeWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());

            // Complete B
            assertEquals(1, jobQueue.size());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("workflow fails when one job in parallel branch fails")
        void parallelBranchFailure() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();

            WorkflowJob jobA = makeJob(a, "A", List.of());
            WorkflowJob jobB = makeJob(b, "B", List.of(a));
            WorkflowJob jobC = makeJob(c, "C", List.of(a));

            Workflow workflow = registerAndExecute("parallel-failure", List.of(jobA, jobB, jobC));

            // Execute A
            tick(workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // B and C enqueued
            tick(workflow.getId());
            assertEquals(2, jobQueue.size());

            // Fail B
            Job dequeuedB = jobQueue.dequeue();
            simulateJobFailure(dequeuedB, workflow.getId());

            // Workflow should FAIL after C completes
            assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());
            
            Job dequeuedC = jobQueue.dequeue();
            simulateJobCompletion(dequeuedC, workflow.getId());
            assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("workflow can be reset after failure and re-executed")
        void resetAndReexecuteAfterFailure() {
            WorkflowJob job = makeJob("single");
            Workflow workflow = registerAndExecute("reset-after-failure", List.of(job));

            // Execute and fail
            tick(workflow.getId());
            Job dequeued = jobQueue.dequeue();
            simulateJobFailure(dequeued, workflow.getId());

            assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());

            // Reset workflow
            workflowManager.resetWorkflow(workflow.getId());
            dagScheduler.forgetWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.DRAFT, workflowManager.getWorkflow(workflow.getId()).getStatus());

            // Re-execute
            workflowManager.executeWorkflow(workflow.getId());
            assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());

            // This time complete successfully
            tick(workflow.getId());
            Job newDequeued = jobQueue.dequeue();
            simulateJobCompletion(newDequeued, workflow.getId());

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("rapid state transitions handled correctly")
        void rapidStateTransitions() {
            WorkflowJob job = makeJob("single");
            Workflow workflow = registerAndExecute("rapid-transitions", List.of(job));

            // Rapid pause/resume cycle
            for (int i = 0; i < 10; i++) {
                workflowManager.pauseWorkflow(workflow.getId());
                assertEquals(WorkflowStatus.PAUSED, workflowManager.getWorkflow(workflow.getId()).getStatus());
                workflowManager.resumeWorkflow(workflow.getId());
                assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());
            }

            // Workflow should still be RUNNING
            assertEquals(WorkflowStatus.RUNNING, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }
    }

    // =========================================================================
    // PRESSURE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Pressure Tests")
    class PressureTests {

        @Test
        @DisplayName("concurrent workflow registration (50 workflows)")
        void concurrentRegistration() throws InterruptedException {
            int numWorkflows = 50;
            CountDownLatch latch = new CountDownLatch(numWorkflows);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(10);

            IntStream.range(0, numWorkflows).forEach(i -> {
                executor.submit(() -> {
                    try {
                        WorkflowJob job = makeJob("job-" + i);
                        Workflow workflow = Workflow.newWorkflow("wf-" + i)
                                .addJob(job)
                                .build();
                        workflowManager.registerWorkflow(workflow);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Registration should complete within timeout");
            executor.shutdown();

            assertEquals(numWorkflows, successCount.get(), "All registrations should succeed");
            assertEquals(0, failureCount.get(), "No failures should occur");
            assertEquals(numWorkflows, workflowManager.workflowCount());
        }

        @Test
        @DisplayName("concurrent workflow execution (20 workflows with jobs)")
        void concurrentExecution() throws InterruptedException {
            int numWorkflows = 20;
            List<UUID> workflowIds = new ArrayList<>();

            // Register workflows
            for (int i = 0; i < numWorkflows; i++) {
                WorkflowJob job = makeJob("job-" + i);
                Workflow workflow = registerAndExecute("concurrent-wf-" + i, List.of(job));
                workflowIds.add(workflow.getId());
            }

            // Tick all workflows
            workflowIds.forEach(Phase1IntegrationTest.this::tick);
            assertEquals(numWorkflows, jobQueue.size());

            // Complete all jobs concurrently
            CountDownLatch latch = new CountDownLatch(numWorkflows);
            ExecutorService executor = Executors.newFixedThreadPool(10);

            for (int i = 0; i < numWorkflows; i++) {
                executor.submit(() -> {
                    try {
                        Job job = jobQueue.dequeue();
                        int idx = Integer.parseInt(job.getPayload().split("-")[1]);
                        UUID wfId = workflowIds.get(idx);
                        simulateJobCompletion(job, wfId);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All jobs should complete within timeout");
            executor.shutdown();

            // Verify all workflows completed
            long completedCount = workflowIds.stream()
                    .map(id -> workflowManager.getWorkflow(id))
                    .filter(wf -> wf.getStatus() == WorkflowStatus.COMPLETED)
                    .count();
            assertEquals(numWorkflows, completedCount);
        }

        @Test
        @DisplayName("high volume: 100 jobs in single workflow")
        void highVolumeSingleWorkflow100Jobs() {
            int numJobs = 100;
            UUID root = UUID.randomUUID();
            List<WorkflowJob> jobs = new ArrayList<>();
            jobs.add(makeJob(root, "root", List.of()));

            // All other jobs depend on root (fan-out)
            for (int i = 0; i < numJobs - 1; i++) {
                jobs.add(makeJob(UUID.randomUUID(), "job-" + i, List.of(root)));
            }

            Workflow workflow = registerAndExecute("high-volume", jobs);

            // Execute root
            tick(workflow.getId());
            simulateJobCompletion(jobQueue.dequeue(), workflow.getId());

            // All 99 dependent jobs should be enqueued up to the max concurrent limit (10)
            tick(workflow.getId());
            assertEquals(10, jobQueue.size());

            // Complete all jobs
            for (int i = 0; i < numJobs - 1; i++) {
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
            assertTrue(jobQueue.isEmpty());
        }

        @Test
        @DisplayName("stress test: rapid tick of workflow with many jobs")
        void rapidTickStress() {
            int numJobs = 50;
            List<WorkflowJob> jobs = new ArrayList<>();

            // Create a chain: job0 → job1 → job2 → ... → job49
            UUID prevId = null;
            for (int i = 0; i < numJobs; i++) {
                UUID id = UUID.randomUUID();
                jobs.add(makeJob(id, "job-" + i, prevId == null ? List.of() : List.of(prevId)));
                prevId = id;
            }

            Workflow workflow = registerAndExecute("rapid-tick", jobs);

            // Rapidly tick and process
            for (int i = 0; i < numJobs; i++) {
                // Tick multiple times rapidly
                for (int t = 0; t < 5; t++) {
                    tick(workflow.getId());
                }
                assertEquals(1, jobQueue.size(), "Iteration " + i);
                simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
        }

        @Test
        @DisplayName("concurrent pause/resume under load (no deadlocks)")
        void concurrentPauseResumeUnderLoad() throws InterruptedException {
            int numWorkflows = 30;
            List<UUID> workflowIds = new ArrayList<>();

            // Register and execute workflows
            for (int i = 0; i < numWorkflows; i++) {
                WorkflowJob job = makeJob("job-" + i);
                Workflow workflow = registerAndExecute("stress-wf-" + i, List.of(job));
                workflowIds.add(workflow.getId());
            }

            // Concurrently pause and resume
            CountDownLatch latch = new CountDownLatch(numWorkflows * 2);
            ExecutorService executor = Executors.newFixedThreadPool(10);

            for (UUID wfId : workflowIds) {
                executor.submit(() -> {
                    try {
                        workflowManager.pauseWorkflow(wfId);
                    } finally {
                        latch.countDown();
                    }
                });
                executor.submit(() -> {
                    try {
                        workflowManager.resumeWorkflow(wfId);
                    } catch (Exception e) {
                        // Some might fail if already paused/resumed, that's OK
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Should complete without deadlock within 10 seconds
            assertTrue(latch.await(10, TimeUnit.SECONDS), "No deadlocks should occur");
            executor.shutdown();
        }

        @Test
        @DisplayName("large DAG (50 nodes, 5 levels) executes correctly")
        void largeDag50Nodes5Levels() {
            int levels = 5;
            int nodesPerLevel = 10;
            List<WorkflowJob> allJobs = new ArrayList<>();
            List<List<UUID>> levelIds = new ArrayList<>();

            // Build DAG level by level
            for (int level = 0; level < levels; level++) {
                List<UUID> currentLevelIds = new ArrayList<>();
                List<UUID> prevLevelIds = level == 0 ? List.of() : levelIds.get(level - 1);

                for (int node = 0; node < nodesPerLevel; node++) {
                    UUID id = UUID.randomUUID();
                    currentLevelIds.add(id);
                    allJobs.add(makeJob(id, "L" + level + "-N" + node, prevLevelIds));
                }

                levelIds.add(currentLevelIds);
            }

            Workflow workflow = registerAndExecute("large-dag", allJobs);

            // Execute level by level
            for (int level = 0; level < levels; level++) {
                tick(workflow.getId());
                assertEquals(nodesPerLevel, jobQueue.size(), "Level " + level);

                for (int node = 0; node < nodesPerLevel; node++) {
                    simulateJobCompletion(jobQueue.dequeue(), workflow.getId());
                }
            }

            assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
            assertTrue(jobQueue.isEmpty());
        }

        @Test
        @DisplayName("system stability: 100 complete-fail-reset cycles")
        void stabilityCycles() {
            WorkflowJob job = makeJob("cyclic-job");
            Workflow workflow = registerAndExecute("stability-test", List.of(job));

            for (int i = 0; i < 100; i++) {
                // Execute
                tick(workflow.getId());
                if (jobQueue.isEmpty()) {
                    throw new RuntimeException("Queue is empty at iteration " + i);
                }
                if (jobQueue.size() != 1) {
                    System.err.println("CRITICAL FAILURE at iter " + i + ", queue size " + jobQueue.size());
                    List<Job> queued = new ArrayList<>();
                    while(!jobQueue.isEmpty()) queued.add(jobQueue.dequeue());
                    for (Job qj : queued) System.err.println("IN QUEUE: " + qj.getId() + " status " + qj.getStatus());
                    throw new RuntimeException("Queue size not 1");
                }
                assertEquals(1, jobQueue.size());

                // Alternate between success and failure
                if (i % 2 == 0) {
                    Job dequeued = jobQueue.dequeue();
                    simulateJobCompletion(dequeued, workflow.getId());
                    assertEquals(WorkflowStatus.COMPLETED, workflowManager.getWorkflow(workflow.getId()).getStatus());
                } else {
                    Job dequeued = jobQueue.dequeue();
                    simulateJobFailure(dequeued, workflow.getId());
                    assertEquals(WorkflowStatus.FAILED, workflowManager.getWorkflow(workflow.getId()).getStatus());
                }

                // Reset for next iteration
                workflowManager.resetWorkflow(workflow.getId());
                dagScheduler.forgetWorkflow(workflow.getId());
                assertEquals(WorkflowStatus.DRAFT, workflowManager.getWorkflow(workflow.getId()).getStatus());
                workflowManager.executeWorkflow(workflow.getId());
            }
        }
    }
}
