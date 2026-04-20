package com.doe.manager;

import com.doe.manager.server.TestManagerServerBuilder;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.scheduler.DagScheduler;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.scheduler.JobResultListener;
import com.doe.manager.workflow.WorkflowManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 Manual Smoke Test Harness.
 *
 * <p>This test harness manually exercises the complete in-memory workflow engine
 * with a diamond pattern workflow (A→B, C→D) to verify:
 * <ul>
 *   <li>Workflow registration and execution</li>
 *   <li>DAG-aware job scheduling</li>
 *   <li>Correct execution order (A first, then B and C in parallel, then D)</li>
 *   <li>Workflow reaches COMPLETED status</li>
 * </ul>
 *
 * <p>Run this test manually to verify Phase 1 works end-to-end.
 * Can also be run via: {@code mvn test -Dtest=Phase1SmokeTest}
 */
public class Phase1SmokeTest {

    public static void main(String[] args) {
        System.out.println("=== Phase 1 Smoke Test: Diamond Pattern Workflow ===\n");

        // Initialize components
        WorkflowManager workflowManager = new WorkflowManager();
        JobQueue jobQueue = new JobQueue(null, 1000);
        DagScheduler dagScheduler = new DagScheduler(workflowManager, jobQueue, 60_000L, true, 10, TestManagerServerBuilder.NO_OP_LISTENER);
        JobResultListener listener = new JobResultListener(workflowManager, dagScheduler);
        dagScheduler.start();

        try {
            testDiamondPattern(workflowManager, jobQueue, dagScheduler, listener);
            System.out.println("\n✅ All smoke tests PASSED!");
        } catch (Exception e) {
            System.err.println("\n❌ Smoke test FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testDiamondPattern(
            WorkflowManager workflowManager,
            JobQueue jobQueue,
            DagScheduler dagScheduler,
            JobResultListener listener) {

        System.out.println("Step 1: Creating diamond pattern workflow (A→B, C→D)...");

        // Create diamond: A → B, C → D
        UUID jobIdA = UUID.randomUUID();
        UUID jobIdB = UUID.randomUUID();
        UUID jobIdC = UUID.randomUUID();
        UUID jobIdD = UUID.randomUUID();

        Job jobA = Job.newJob("task-A")
                .id(jobIdA)
                .status(JobStatus.PENDING)
                .timeoutMs(60_000L)
                .build();
        Job jobB = Job.newJob("task-B")
                .id(jobIdB)
                .status(JobStatus.PENDING)
                .timeoutMs(60_000L)
                .build();
        Job jobC = Job.newJob("task-C")
                .id(jobIdC)
                .status(JobStatus.PENDING)
                .timeoutMs(60_000L)
                .build();
        Job jobD = Job.newJob("task-D")
                .id(jobIdD)
                .status(JobStatus.PENDING)
                .timeoutMs(60_000L)
                .build();

        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(1).dependencies(List.of(jobIdA)).build();
        WorkflowJob wjC = WorkflowJob.fromJob(jobC).dagIndex(2).dependencies(List.of(jobIdA)).build();
        WorkflowJob wjD = WorkflowJob.fromJob(jobD).dagIndex(3).dependencies(List.of(jobIdB, jobIdC)).build();

        Workflow workflow = Workflow.newWorkflow("diamond-smoke-test")
                .addJobs(List.of(wjA, wjB, wjC, wjD))
                .build();

        System.out.println("  Workflow ID: " + workflow.getId());
        System.out.println("  Jobs: A(root) → B,C(parallel) → D(final)\n");

        // Step 2: Register workflow
        System.out.println("Step 2: Registering workflow...");
        Workflow registered = workflowManager.registerWorkflow(workflow);
        assert registered.getStatus() == WorkflowStatus.DRAFT : "Workflow should be in DRAFT status";
        System.out.println("  ✓ Workflow registered with status: " + registered.getStatus());
        System.out.println("  ✓ Workflow count: " + workflowManager.workflowCount() + "\n");

        // Step 3: Execute workflow
        System.out.println("Step 3: Executing workflow...");
        workflowManager.executeWorkflow(workflow.getId());
        Workflow running = workflowManager.getWorkflow(workflow.getId());
        assert running.getStatus() == WorkflowStatus.RUNNING : "Workflow should be RUNNING";
        System.out.println("  ✓ Workflow status: " + running.getStatus() + "\n");

        // Step 4: Trigger scheduler - only A should be enqueued
        System.out.println("Step 4: Triggering DAG scheduler...");
        dagScheduler.onWorkflowJobChanged(workflow.getId());
        assert jobQueue.size() == 1 : "Only root job A should be enqueued, got: " + jobQueue.size();
        Job dequeuedA = jobQueue.dequeue();
        assert dequeuedA.getId().equals(jobIdA) : "Dequeued job should be A";
        System.out.println("  ✓ Only job A enqueued (correct - it's the root)");
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 5: Complete job A - B and C should be enqueued
        System.out.println("Step 5: Completing job A...");
        simulateJobCompletion(dequeuedA, workflow.getId(), dagScheduler, listener);

        assert jobQueue.size() == 2 : "Jobs B and C should be enqueued, got: " + jobQueue.size();
        System.out.println("  ✓ Jobs B and C enqueued after A completed");
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 6: Verify B and C are in the queue
        System.out.println("Step 6: Dequeuing B and C...");
        Job dequeuedB = jobQueue.dequeue();
        Job dequeuedC = jobQueue.dequeue();
        boolean bAndC = (dequeuedB.getId().equals(jobIdB) && dequeuedC.getId().equals(jobIdC)) ||
                        (dequeuedB.getId().equals(jobIdC) && dequeuedC.getId().equals(jobIdB));
        assert bAndC : "Queue should contain B and C";
        System.out.println("  ✓ Job B dequeued: " + (dequeuedB.getId().equals(jobIdB) || dequeuedB.getId().equals(jobIdC)));
        System.out.println("  ✓ Job C dequeued: " + (dequeuedC.getId().equals(jobIdB) || dequeuedC.getId().equals(jobIdC)));
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 7: Complete job B - D should NOT be enqueued yet
        System.out.println("Step 7: Completing job B (D should NOT be enqueued yet)...");
        simulateJobCompletion(dequeuedB, workflow.getId(), dagScheduler, listener);
        assert jobQueue.size() == 0 : "D should NOT be enqueued until C completes, got: " + jobQueue.size();
        System.out.println("  ✓ Job D NOT enqueued (correct - waiting for C)");
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 8: Complete job C - D should now be enqueued
        System.out.println("Step 8: Completing job C (D should now be enqueued)...");
        simulateJobCompletion(dequeuedC, workflow.getId(), dagScheduler, listener);
        assert jobQueue.size() == 1 : "Job D should be enqueued, got: " + jobQueue.size();
        Job dequeuedD = jobQueue.dequeue();
        assert dequeuedD.getId().equals(jobIdD) : "Dequeued job should be D";
        System.out.println("  ✓ Job D enqueued after C completed");
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 9: Complete job D - workflow should complete
        System.out.println("Step 9: Completing job D...");
        simulateJobCompletion(dequeuedD, workflow.getId(), dagScheduler, listener);

        Workflow completed = workflowManager.getWorkflow(workflow.getId());
        assert completed.getStatus() == WorkflowStatus.COMPLETED : "Workflow should be COMPLETED, got: " + completed.getStatus();
        System.out.println("  ✓ Workflow status: " + completed.getStatus());
        System.out.println("  ✓ Job queue size: " + jobQueue.size() + "\n");

        // Step 10: Verify all jobs completed
        System.out.println("Step 10: Verifying all job statuses...");
        assert completed.getJob(jobIdA).getJob().getStatus() == JobStatus.COMPLETED : "Job A should be COMPLETED";
        assert completed.getJob(jobIdB).getJob().getStatus() == JobStatus.COMPLETED : "Job B should be COMPLETED";
        assert completed.getJob(jobIdC).getJob().getStatus() == JobStatus.COMPLETED : "Job C should be COMPLETED";
        assert completed.getJob(jobIdD).getJob().getStatus() == JobStatus.COMPLETED : "Job D should be COMPLETED";
        System.out.println("  ✓ Job A: " + completed.getJob(jobIdA).getJob().getStatus());
        System.out.println("  ✓ Job B: " + completed.getJob(jobIdB).getJob().getStatus());
        System.out.println("  ✓ Job C: " + completed.getJob(jobIdC).getJob().getStatus());
        System.out.println("  ✓ Job D: " + completed.getJob(jobIdD).getJob().getStatus() + "\n");

        System.out.println("=== Diamond Pattern Workflow Completed Successfully ===");
    }

    private static void simulateJobCompletion(
            Job job,
            UUID workflowId,
            DagScheduler dagScheduler,
            JobResultListener listener) {
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        listener.onJobCompleted(job.getId(), null, "success", Instant.now());
    }
}
