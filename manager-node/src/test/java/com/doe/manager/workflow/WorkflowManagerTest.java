package com.doe.manager.workflow;

import com.doe.core.model.Job;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkflowManager}.
 * Covers all lifecycle operations, edge cases, and concurrent access.
 */
class WorkflowManagerTest {

    private WorkflowManager manager;

    @BeforeEach
    void setUp() {
        manager = new WorkflowManager();
    }

    // ──── Helpers ───────────────────────────────────────────────────────────

    private Workflow createWorkflow(String name, List<WorkflowJob> jobs) {
        Workflow.Builder builder = Workflow.newWorkflow(name);
        for (WorkflowJob job : jobs) {
            builder.addJob(job);
        }
        return builder.build();
    }

    private WorkflowJob makeWorkflowJob(String payload) {
        return makeWorkflowJob(payload, List.of());
    }

    private WorkflowJob makeWorkflowJob(String payload, List<UUID> dependencies) {
        Job job = Job.newJob(payload).timeoutMs(60000L).build();
        return WorkflowJob.fromJob(job)
                .dagIndex(0)
                .dependencies(dependencies)
                .build();
    }

    // ──── registerWorkflow ──────────────────────────────────────────────────

    @Test
    @DisplayName("registerWorkflow stores workflow in DRAFT status")
    void registerWorkflow_draftStatus() {
        WorkflowJob job = makeWorkflowJob("echo hello");
        Workflow workflow = createWorkflow("test-workflow", List.of(job));

        Workflow registered = manager.registerWorkflow(workflow);

        assertNotNull(registered);
        assertEquals(WorkflowStatus.DRAFT, registered.getStatus());
        assertEquals(1, manager.workflowCount());
    }

    @Test
    @DisplayName("registerWorkflow rejects non-DRAFT workflows")
    void registerWorkflow_nonDraft_throws() {
        WorkflowJob job = makeWorkflowJob("echo hello");
        Workflow workflow = Workflow.newWorkflow("test")
                .status(WorkflowStatus.RUNNING)
                .addJob(job)
                .build();

        assertThrows(WorkflowException.class, () -> manager.registerWorkflow(workflow));
    }

    @Test
    @DisplayName("registerWorkflow rejects workflow with self-dependency")
    void registerWorkflow_selfDependency_throws() {
        Job job = Job.newJob("self").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job)
                .dagIndex(0)
                .dependencies(List.of(job.getId()))
                .build();
        Workflow workflow = createWorkflow("bad-dag", List.of(wj));

        assertThrows(WorkflowException.class, () -> manager.registerWorkflow(workflow));
    }

    @Test
    @DisplayName("registerWorkflow rejects workflow with missing dependency")
    void registerWorkflow_missingDependency_throws() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(1).dependencies(List.of(jobA.getId(), UUID.randomUUID())).build();
        Workflow workflow = createWorkflow("bad-dag", List.of(wjA, wjB));

        assertThrows(WorkflowException.class, () -> manager.registerWorkflow(workflow));
    }

    @Test
    @DisplayName("registerWorkflow rejects workflow with cycle")
    void registerWorkflow_cycle_throws() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of(jobB.getId())).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(1).dependencies(List.of(jobA.getId())).build();
        Workflow workflow = createWorkflow("cyclic-dag", List.of(wjA, wjB));

        assertThrows(WorkflowException.class, () -> manager.registerWorkflow(workflow));
    }

    @Test
    @DisplayName("registerWorkflow accepts valid DAG with multiple root nodes")
    void registerWorkflow_validMultiRoot() {
        Job a = Job.newJob("a").timeoutMs(60000L).build();
        Job b = Job.newJob("b").timeoutMs(60000L).build();
        Job c = Job.newJob("c").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(a).dagIndex(0).dependencies(List.of()).build();
        WorkflowJob wjB = WorkflowJob.fromJob(b).dagIndex(1).dependencies(List.of()).build();
        WorkflowJob wjC = WorkflowJob.fromJob(c).dagIndex(2).dependencies(List.of(a.getId(), b.getId())).build();
        Workflow workflow = createWorkflow("diamond", List.of(wjA, wjB, wjC));

        Workflow registered = manager.registerWorkflow(workflow);

        assertEquals(WorkflowStatus.DRAFT, registered.getStatus());
        assertEquals(1, manager.workflowCount());
        assertEquals(3, registered.jobCount());
    }

    @Test
    @DisplayName("registerWorkflow null throws NullPointerException")
    void registerWorkflow_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.registerWorkflow(null));
    }

    // ──── getWorkflow ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getWorkflow returns registered workflow")
    void getWorkflow_exists() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        Workflow found = manager.getWorkflow(workflow.getId());

        assertNotNull(found);
        assertEquals(workflow.getId(), found.getId());
    }

    @Test
    @DisplayName("getWorkflow returns null for unknown ID")
    void getWorkflow_notFound_returnsNull() {
        assertNull(manager.getWorkflow(UUID.randomUUID()));
    }

    @Test
    @DisplayName("getWorkflow null throws NullPointerException")
    void getWorkflow_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.getWorkflow(null));
    }

    // ──── deleteWorkflow ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteWorkflow removes DRAFT workflow")
    void deleteWorkflow_draft() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        manager.deleteWorkflow(workflow.getId());

        assertNull(manager.getWorkflow(workflow.getId()));
        assertEquals(0, manager.workflowCount());
    }

    @Test
    @DisplayName("deleteWorkflow removes PAUSED workflow")
    void deleteWorkflow_paused() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        manager.deleteWorkflow(workflow.getId());

        assertNull(manager.getWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("deleteWorkflow throws on RUNNING workflow")
    void deleteWorkflow_running_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());

        assertThrows(WorkflowException.class, () -> manager.deleteWorkflow(workflow.getId()));
        // Workflow should still exist
        assertNotNull(manager.getWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("deleteWorkflow removes COMPLETED workflow")
    void deleteWorkflow_completed() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.completeWorkflow(workflow.getId());

        manager.deleteWorkflow(workflow.getId());
        assertNull(manager.getWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("deleteWorkflow removes FAILED workflow")
    void deleteWorkflow_failed() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.failWorkflow(workflow.getId());

        manager.deleteWorkflow(workflow.getId());
        assertNull(manager.getWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("deleteWorkflow throws on unknown ID")
    void deleteWorkflow_notFound_throws() {
        assertThrows(WorkflowException.class, () -> manager.deleteWorkflow(UUID.randomUUID()));
    }

    @Test
    @DisplayName("deleteWorkflow null throws NullPointerException")
    void deleteWorkflow_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.deleteWorkflow(null));
    }

    // ──── executeWorkflow ───────────────────────────────────────────────────

    @Test
    @DisplayName("executeWorkflow transitions DRAFT → RUNNING")
    void executeWorkflow_draftToRunning() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        Workflow running = manager.executeWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.RUNNING, running.getStatus());
        assertEquals(WorkflowStatus.RUNNING, manager.getWorkflow(workflow.getId()).getStatus());
    }

    @Test
    @DisplayName("executeWorkflow throws on non-DRAFT workflow")
    void executeWorkflow_nonDraft_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());

        // Already RUNNING — cannot execute again
        assertThrows(WorkflowException.class, () -> manager.executeWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("executeWorkflow throws on unknown ID")
    void executeWorkflow_notFound_throws() {
        assertThrows(WorkflowException.class, () -> manager.executeWorkflow(UUID.randomUUID()));
    }

    @Test
    @DisplayName("executeWorkflow null throws NullPointerException")
    void executeWorkflow_null_throws() {
        assertThrows(NullPointerException.class, () -> manager.executeWorkflow(null));
    }

    // ──── pauseWorkflow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("pauseWorkflow transitions RUNNING → PAUSED")
    void pauseWorkflow_runningToPaused() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());

        Workflow paused = manager.pauseWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.PAUSED, paused.getStatus());
        assertEquals(WorkflowStatus.PAUSED, manager.getWorkflow(workflow.getId()).getStatus());
    }

    @Test
    @DisplayName("pauseWorkflow throws on non-RUNNING workflow")
    void pauseWorkflow_nonRunning_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        // Still in DRAFT — cannot pause
        assertThrows(WorkflowException.class, () -> manager.pauseWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("pauseWorkflow throws on unknown ID")
    void pauseWorkflow_notFound_throws() {
        assertThrows(WorkflowException.class, () -> manager.pauseWorkflow(UUID.randomUUID()));
    }

    // ──── resumeWorkflow ────────────────────────────────────────────────────

    @Test
    @DisplayName("resumeWorkflow transitions PAUSED → RUNNING")
    void resumeWorkflow_pausedToRunning() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        Workflow resumed = manager.resumeWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.RUNNING, resumed.getStatus());
        assertEquals(WorkflowStatus.RUNNING, manager.getWorkflow(workflow.getId()).getStatus());
    }

    @Test
    @DisplayName("resumeWorkflow throws on non-PAUSED workflow")
    void resumeWorkflow_nonPaused_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        // Still in DRAFT — cannot resume
        assertThrows(WorkflowException.class, () -> manager.resumeWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("resumeWorkflow throws on unknown ID")
    void resumeWorkflow_notFound_throws() {
        assertThrows(WorkflowException.class, () -> manager.resumeWorkflow(UUID.randomUUID()));
    }

    // ──── resetWorkflow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("resetWorkflow transitions PAUSED → DRAFT and resets job statuses")
    void resetWorkflow_pausedToDraft() {
        Job job = Job.newJob("echo").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        Workflow reset = manager.resetWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        assertEquals(com.doe.core.model.JobStatus.PENDING, reset.getJob(job.getId()).getJob().getStatus());
    }

    @Test
    @DisplayName("resetWorkflow transitions COMPLETED → DRAFT and resets job statuses")
    void resetWorkflow_completedToDraft() {
        Job job = Job.newJob("echo").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.completeWorkflow(workflow.getId());

        Workflow reset = manager.resetWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        assertEquals(com.doe.core.model.JobStatus.PENDING, reset.getJob(job.getId()).getJob().getStatus());
    }

    @Test
    @DisplayName("resetWorkflow transitions FAILED → DRAFT and resets job statuses")
    void resetWorkflow_failedToDraft() {
        Job job = Job.newJob("echo").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.failWorkflow(workflow.getId());

        Workflow reset = manager.resetWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        assertEquals(com.doe.core.model.JobStatus.PENDING, reset.getJob(job.getId()).getJob().getStatus());
    }

    @Test
    @DisplayName("resetWorkflow throws on RUNNING workflow")
    void resetWorkflow_running_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());

        assertThrows(WorkflowException.class, () -> manager.resetWorkflow(workflow.getId()));
    }

    @Test
    @DisplayName("resetWorkflow on DRAFT resets job statuses but stays DRAFT")
    void resetWorkflow_draft_resetsJobs() {
        Job job = Job.newJob("echo").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj));
        manager.registerWorkflow(workflow);

        Workflow reset = manager.resetWorkflow(workflow.getId());

        assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        assertEquals(com.doe.core.model.JobStatus.PENDING, reset.getJob(job.getId()).getJob().getStatus());
    }

    @Test
    @DisplayName("resetWorkflow throws on unknown ID")
    void resetWorkflow_notFound_throws() {
        assertThrows(WorkflowException.class, () -> manager.resetWorkflow(UUID.randomUUID()));
    }

    // ──── updateWorkflow ────────────────────────────────────────────────────

    @Test
    @DisplayName("updateWorkflow replaces definition in DRAFT state")
    void updateWorkflow_draft() {
        Job job1 = Job.newJob("old").timeoutMs(60000L).build();
        WorkflowJob wj1 = WorkflowJob.fromJob(job1).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj1));
        manager.registerWorkflow(workflow);

        Job job2 = Job.newJob("new").timeoutMs(60000L).build();
        WorkflowJob wj2 = WorkflowJob.fromJob(job2).dagIndex(0).dependencies(List.of()).build();
        Workflow updated = createWorkflow("test-updated", List.of(wj2));
        // Ensure same ID
        updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(wj2)
                .build();

        Workflow result = manager.updateWorkflow(workflow.getId(), updated);

        assertEquals(1, result.jobCount());
        assertEquals("new", result.getJob(job2.getId()).getJob().getPayload());
        assertEquals(WorkflowStatus.DRAFT, result.getStatus());
    }

    @Test
    @DisplayName("updateWorkflow replaces definition in PAUSED state")
    void updateWorkflow_paused() {
        Job job1 = Job.newJob("old").timeoutMs(60000L).build();
        WorkflowJob wj1 = WorkflowJob.fromJob(job1).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wj1));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        Job job2 = Job.newJob("new").timeoutMs(60000L).build();
        WorkflowJob wj2 = WorkflowJob.fromJob(job2).dagIndex(0).dependencies(List.of()).build();
        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(wj2)
                .build();

        Workflow result = manager.updateWorkflow(workflow.getId(), updated);

        assertEquals(WorkflowStatus.PAUSED, result.getStatus());
        assertEquals("new", result.getJob(job2.getId()).getJob().getPayload());
    }

    @Test
    @DisplayName("updateWorkflow throws on RUNNING workflow")
    void updateWorkflow_running_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());

        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(makeWorkflowJob("new"))
                .build();

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), updated));
    }

    @Test
    @DisplayName("updateWorkflow throws on COMPLETED workflow")
    void updateWorkflow_completed_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.completeWorkflow(workflow.getId());

        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(makeWorkflowJob("new"))
                .build();

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), updated));
    }

    @Test
    @DisplayName("updateWorkflow throws on FAILED workflow")
    void updateWorkflow_failed_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.failWorkflow(workflow.getId());

        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(makeWorkflowJob("new"))
                .build();

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), updated));
    }

    @Test
    @DisplayName("updateWorkflow rejects invalid DAG")
    void updateWorkflow_invalidDag_throws() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wjA));
        manager.registerWorkflow(workflow);

        // New workflow with self-dependency
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB)
                .dagIndex(0)
                .dependencies(List.of(jobB.getId()))
                .build();
        Workflow invalidWorkflow = Workflow.newWorkflow("test-bad")
                .id(workflow.getId())
                .addJob(wjB)
                .build();

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), invalidWorkflow));
    }

    @Test
    @DisplayName("updateWorkflow throws on ID mismatch")
    void updateWorkflow_idMismatch_throws() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        Workflow differentId = createWorkflow("other", List.of(makeWorkflowJob("other")));

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), differentId));
    }

    // ──── updateWorkflow — job status consistency ───────────────────────────

    @Test
    @DisplayName("updateWorkflow in DRAFT rejects jobs that are not PENDING")
    void updateWorkflow_draft_nonPendingJobs_throws() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wjA));
        manager.registerWorkflow(workflow);

        // New workflow with a job in COMPLETED status
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        jobB.transition(com.doe.core.model.JobStatus.ASSIGNED);
        jobB.transition(com.doe.core.model.JobStatus.RUNNING);
        jobB.transition(com.doe.core.model.JobStatus.COMPLETED);
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(0).dependencies(List.of()).build();
        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(wjB)
                .build();

        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), updated));
    }

    @Test
    @DisplayName("updateWorkflow in PAUSED rejects job ahead of dependency")
    void updateWorkflow_paused_jobAheadOfDep_throws() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        // jobB depends on jobA, but jobB is COMPLETED while jobA is still PENDING
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(1)
                .dependencies(List.of(jobA.getId()))
                .build();
        wjB.getJob().transition(com.doe.core.model.JobStatus.ASSIGNED);
        wjB.getJob().transition(com.doe.core.model.JobStatus.RUNNING);
        wjB.getJob().transition(com.doe.core.model.JobStatus.COMPLETED);

        Workflow workflow = createWorkflow("test", List.of(wjA, wjB));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        // Try to update with the same invalid structure
        assertThrows(WorkflowException.class, () -> manager.updateWorkflow(workflow.getId(), workflow));
    }

    @Test
    @DisplayName("updateWorkflow in PAUSED accepts valid dependency ordering")
    void updateWorkflow_paused_validOrdering_succeeds() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        // jobB depends on jobA, jobA is COMPLETED, jobB is PENDING — valid
        jobA.transition(com.doe.core.model.JobStatus.ASSIGNED);
        jobA.transition(com.doe.core.model.JobStatus.RUNNING);
        jobA.transition(com.doe.core.model.JobStatus.COMPLETED);

        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(1)
                .dependencies(List.of(jobA.getId()))
                .build();

        Workflow workflow = createWorkflow("test", List.of(wjA, wjB));
        manager.registerWorkflow(workflow);
        manager.executeWorkflow(workflow.getId());
        manager.pauseWorkflow(workflow.getId());

        // Update with valid ordering — should succeed
        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(wjA)
                .addJob(wjB)
                .build();

        Workflow result = manager.updateWorkflow(workflow.getId(), updated);
        assertEquals(WorkflowStatus.PAUSED, result.getStatus());
    }

    @Test
    @DisplayName("updateWorkflow in DRAFT with all PENDING jobs succeeds")
    void updateWorkflow_draft_allPending_succeeds() {
        Job jobA = Job.newJob("a").timeoutMs(60000L).build();
        WorkflowJob wjA = WorkflowJob.fromJob(jobA).dagIndex(0).dependencies(List.of()).build();
        Workflow workflow = createWorkflow("test", List.of(wjA));
        manager.registerWorkflow(workflow);

        Job jobB = Job.newJob("b").timeoutMs(60000L).build();
        WorkflowJob wjB = WorkflowJob.fromJob(jobB).dagIndex(0).dependencies(List.of()).build();
        Workflow updated = Workflow.newWorkflow("test-updated")
                .id(workflow.getId())
                .addJob(wjB)
                .build();

        Workflow result = manager.updateWorkflow(workflow.getId(), updated);
        assertEquals(WorkflowStatus.DRAFT, result.getStatus());
        assertEquals(com.doe.core.model.JobStatus.PENDING, result.getJob(jobB.getId()).getJob().getStatus());
    }

    // ──── listWorkflows ─────────────────────────────────────────────────────

    @Test
    @DisplayName("listWorkflows returns all workflows")
    void listWorkflows_all() {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow w1 = createWorkflow("w1", List.of(job));
        Workflow w2 = createWorkflow("w2", List.of(job));
        manager.registerWorkflow(w1);
        manager.registerWorkflow(w2);

        List<Workflow> all = manager.listWorkflows();

        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("listWorkflows filters by status")
    void listWorkflows_filterByStatus() {
        Job job = Job.newJob("echo").timeoutMs(60000L).build();
        WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
        Workflow w1 = createWorkflow("w1", List.of(wj));
        Workflow w2 = createWorkflow("w2", List.of(wj));
        manager.registerWorkflow(w1);
        manager.registerWorkflow(w2);
        manager.executeWorkflow(w1.getId());

        List<Workflow> running = manager.listWorkflows(WorkflowStatus.RUNNING);
        List<Workflow> draft = manager.listWorkflows(WorkflowStatus.DRAFT);

        assertEquals(1, running.size());
        assertEquals(1, draft.size());
    }

    @Test
    @DisplayName("listWorkflows returns empty when no workflows match filter")
    void listWorkflows_noMatch() {
        List<Workflow> running = manager.listWorkflows(WorkflowStatus.RUNNING);
        assertTrue(running.isEmpty());
    }

    // ──── Concurrent Access ─────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent reads do not corrupt state")
    void concurrentReads_noRace() throws InterruptedException {
        WorkflowJob job = makeWorkflowJob("echo");
        Workflow workflow = createWorkflow("test", List.of(job));
        manager.registerWorkflow(workflow);

        int threadCount = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    Workflow found = manager.getWorkflow(workflow.getId());
                    if (found == null || found.jobCount() != 1) {
                        errors.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors should occur during concurrent reads");
    }

    @Test
    @DisplayName("concurrent register and execute maintains consistency")
    void concurrentRegisterExecute() throws InterruptedException {
        int count = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(count);

        IntStream.range(0, count).forEach(i -> {
            executor.execute(() -> {
                try {
                    Job job = Job.newJob("job-" + i).timeoutMs(60000L).build();
                    WorkflowJob wj = WorkflowJob.fromJob(job).dagIndex(0).dependencies(List.of()).build();
                    Workflow workflow = createWorkflow("wf-" + i, List.of(wj));
                    manager.registerWorkflow(workflow);
                    manager.executeWorkflow(workflow.getId());
                } catch (WorkflowException e) {
                    // expected for some edge cases, ignore
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        // All workflows should be registered and either DRAFT or RUNNING
        assertEquals(count, manager.workflowCount());
        long nonDraftOrRunning = manager.listWorkflows().stream()
                .filter(w -> w.getStatus() != WorkflowStatus.DRAFT && w.getStatus() != WorkflowStatus.RUNNING)
                .count();
        assertEquals(0, nonDraftOrRunning, "All workflows should be DRAFT or RUNNING");
    }
}
