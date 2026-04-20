package com.doe.manager.workflow;

import com.doe.core.model.WorkflowStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates all valid and invalid workflow state transitions
 * as defined by the {@link WorkflowStatus} state machine.
 *
 * <p>Valid transitions:
 * <pre>
 * DRAFT     → RUNNING
 * RUNNING   → PAUSED, COMPLETED, FAILED
 * PAUSED    → RUNNING, FAILED
 * COMPLETED → (terminal)
 * FAILED    → (terminal)
 * </pre>
 *
 * <p>Additional lifecycle rules enforced by {@link WorkflowManager}:
 * <ul>
 *   <li>DRAFT → DELETED (via deleteWorkflow)</li>
 *   <li>PAUSED → DELETED (via deleteWorkflow)</li>
 *   <li>COMPLETED → DELETED (via deleteWorkflow)</li>
 *   <li>FAILED → DELETED (via deleteWorkflow)</li>
 *   <li>PAUSED → DRAFT (via resetWorkflow)</li>
 *   <li>COMPLETED → DRAFT (via resetWorkflow)</li>
 *   <li>FAILED → DRAFT (via resetWorkflow)</li>
 * </ul>
 */
class WorkflowStatusMachineTest {

    // ──── Direct enum transition tests ──────────────────────────────────────

    @Test
    @DisplayName("DRAFT → RUNNING is valid")
    void draft_toRunning_valid() {
        assertTrue(WorkflowStatus.DRAFT.canTransitionTo(WorkflowStatus.RUNNING));
    }

    @Test
    @DisplayName("DRAFT → PAUSED is invalid")
    void draft_toPaused_invalid() {
        assertFalse(WorkflowStatus.DRAFT.canTransitionTo(WorkflowStatus.PAUSED));
    }

    @Test
    @DisplayName("DRAFT → COMPLETED is invalid")
    void draft_toCompleted_invalid() {
        assertFalse(WorkflowStatus.DRAFT.canTransitionTo(WorkflowStatus.COMPLETED));
    }

    @Test
    @DisplayName("DRAFT → FAILED is invalid")
    void draft_toFailed_invalid() {
        assertFalse(WorkflowStatus.DRAFT.canTransitionTo(WorkflowStatus.FAILED));
    }

    @Test
    @DisplayName("DRAFT → DRAFT is invalid (no self-transition)")
    void draft_toDraft_invalid() {
        assertFalse(WorkflowStatus.DRAFT.canTransitionTo(WorkflowStatus.DRAFT));
    }

    @Test
    @DisplayName("RUNNING → PAUSED is valid")
    void running_toPaused_valid() {
        assertTrue(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.PAUSED));
    }

    @Test
    @DisplayName("RUNNING → COMPLETED is valid")
    void running_toCompleted_valid() {
        assertTrue(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.COMPLETED));
    }

    @Test
    @DisplayName("RUNNING → FAILED is valid")
    void running_toFailed_valid() {
        assertTrue(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.FAILED));
    }

    @Test
    @DisplayName("RUNNING → DRAFT is invalid")
    void running_toDraft_invalid() {
        assertFalse(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.DRAFT));
    }

    @Test
    @DisplayName("RUNNING → RUNNING is invalid (no self-transition)")
    void running_toRunning_invalid() {
        assertFalse(WorkflowStatus.RUNNING.canTransitionTo(WorkflowStatus.RUNNING));
    }

    @Test
    @DisplayName("PAUSED → RUNNING is valid")
    void paused_toRunning_valid() {
        assertTrue(WorkflowStatus.PAUSED.canTransitionTo(WorkflowStatus.RUNNING));
    }

    @Test
    @DisplayName("PAUSED → FAILED is valid")
    void paused_toFailed_valid() {
        assertTrue(WorkflowStatus.PAUSED.canTransitionTo(WorkflowStatus.FAILED));
    }

    @Test
    @DisplayName("PAUSED → DRAFT is invalid (at enum level; resetWorkflow handles this)")
    void paused_toDraft_invalid() {
        assertFalse(WorkflowStatus.PAUSED.canTransitionTo(WorkflowStatus.DRAFT));
    }

    @Test
    @DisplayName("PAUSED → COMPLETED is invalid")
    void paused_toCompleted_invalid() {
        assertFalse(WorkflowStatus.PAUSED.canTransitionTo(WorkflowStatus.COMPLETED));
    }

    @Test
    @DisplayName("PAUSED → PAUSED is invalid (no self-transition)")
    void paused_toPaused_invalid() {
        assertFalse(WorkflowStatus.PAUSED.canTransitionTo(WorkflowStatus.PAUSED));
    }

    @Test
    @DisplayName("COMPLETED → any is invalid (terminal state)")
    void completed_terminal() {
        for (WorkflowStatus target : WorkflowStatus.values()) {
            assertFalse(WorkflowStatus.COMPLETED.canTransitionTo(target),
                    "COMPLETED → " + target + " should be invalid");
        }
    }

    @Test
    @DisplayName("FAILED → any is invalid (terminal state)")
    void failed_terminal() {
        for (WorkflowStatus target : WorkflowStatus.values()) {
            assertFalse(WorkflowStatus.FAILED.canTransitionTo(target),
                    "FAILED → " + target + " should be invalid");
        }
    }

    // ──── Exhaustive transition matrix ──────────────────────────────────────

    @Test
    @DisplayName("exhaustive transition matrix matches expected valid transitions")
    void exhaustiveTransitionMatrix() {
        // Define all valid transitions per the spec
        List<Transition> validTransitions = List.of(
                new Transition(WorkflowStatus.DRAFT, WorkflowStatus.RUNNING),
                new Transition(WorkflowStatus.RUNNING, WorkflowStatus.PAUSED),
                new Transition(WorkflowStatus.RUNNING, WorkflowStatus.COMPLETED),
                new Transition(WorkflowStatus.RUNNING, WorkflowStatus.FAILED),
                new Transition(WorkflowStatus.PAUSED, WorkflowStatus.RUNNING),
                new Transition(WorkflowStatus.PAUSED, WorkflowStatus.FAILED)
        );

        WorkflowStatus[] allStatuses = WorkflowStatus.values();

        for (WorkflowStatus from : allStatuses) {
            for (WorkflowStatus to : allStatuses) {
                boolean expected = validTransitions.contains(new Transition(from, to));
                assertEquals(expected, from.canTransitionTo(to),
                        "%s → %s: expected %s but was %s"
                                .formatted(from, to, expected, !expected));
            }
        }
    }

    // ──── Nested: WorkflowManager-enforced lifecycle rules ──────────────────

    @Nested
    @DisplayName("WorkflowManager-enforced lifecycle rules (beyond enum transitions)")
    class ManagerEnforcedRules {

        @Test
        @DisplayName("deleteWorkflow allows DRAFT → DELETED (removal from store)")
        void delete_draft() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.deleteWorkflow(wf.getId());
            assertNull(mgr.getWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("deleteWorkflow allows PAUSED → DELETED")
        void delete_paused() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            mgr.pauseWorkflow(wf.getId());
            mgr.deleteWorkflow(wf.getId());
            assertNull(mgr.getWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("deleteWorkflow blocks RUNNING → DELETED")
        void delete_running_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            assertThrows(WorkflowException.class, () -> mgr.deleteWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("resetWorkflow allows PAUSED → DRAFT")
        void reset_pausedToDraft() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            mgr.pauseWorkflow(wf.getId());
            var reset = mgr.resetWorkflow(wf.getId());
            assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        }

        @Test
        @DisplayName("resetWorkflow allows COMPLETED → DRAFT")
        void reset_completedToDraft() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            mgr.completeWorkflow(wf.getId());
            var reset = mgr.resetWorkflow(wf.getId());
            assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        }

        @Test
        @DisplayName("resetWorkflow allows FAILED → DRAFT")
        void reset_failedToDraft() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            mgr.failWorkflow(wf.getId());
            var reset = mgr.resetWorkflow(wf.getId());
            assertEquals(WorkflowStatus.DRAFT, reset.getStatus());
        }

        @Test
        @DisplayName("resetWorkflow blocks RUNNING → DRAFT")
        void reset_running_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            assertThrows(WorkflowException.class, () -> mgr.resetWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("executeWorkflow blocks RUNNING → RUNNING (already running)")
        void execute_running_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            assertThrows(WorkflowException.class, () -> mgr.executeWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("pauseWorkflow blocks DRAFT → PAUSED (must be RUNNING)")
        void pause_draft_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            assertThrows(WorkflowException.class, () -> mgr.pauseWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("resumeWorkflow blocks DRAFT → RUNNING (must be PAUSED)")
        void resume_draft_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            assertThrows(WorkflowException.class, () -> mgr.resumeWorkflow(wf.getId()));
        }

        @Test
        @DisplayName("resumeWorkflow blocks RUNNING → RUNNING (must be PAUSED)")
        void resume_running_blocked() {
            WorkflowManager mgr = new WorkflowManager();
            var wf = createSimpleWorkflow(mgr);
            mgr.executeWorkflow(wf.getId());
            assertThrows(WorkflowException.class, () -> mgr.resumeWorkflow(wf.getId()));
        }
    }

    // ──── Full lifecycle path tests ─────────────────────────────────────────

    @Test
    @DisplayName("full lifecycle: DRAFT → RUNNING → PAUSED → RUNNING → COMPLETED")
    void fullLifecycle_happyPath() {
        WorkflowManager mgr = new WorkflowManager();
        var wf = createSimpleWorkflow(mgr);

        assertEquals(WorkflowStatus.DRAFT, mgr.getWorkflow(wf.getId()).getStatus());

        wf = mgr.executeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.RUNNING, wf.getStatus());

        wf = mgr.pauseWorkflow(wf.getId());
        assertEquals(WorkflowStatus.PAUSED, wf.getStatus());

        wf = mgr.resumeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.RUNNING, wf.getStatus());

        mgr.completeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.COMPLETED, mgr.getWorkflow(wf.getId()).getStatus());
    }

    @Test
    @DisplayName("failure lifecycle: DRAFT → RUNNING → FAILED → DRAFT → RUNNING → COMPLETED")
    void failureAndRecovery() {
        WorkflowManager mgr = new WorkflowManager();
        var wf = createSimpleWorkflow(mgr);

        mgr.executeWorkflow(wf.getId());

        mgr.failWorkflow(wf.getId());
        assertEquals(WorkflowStatus.FAILED, mgr.getWorkflow(wf.getId()).getStatus());

        // Reset and retry
        mgr.resetWorkflow(wf.getId());
        assertEquals(WorkflowStatus.DRAFT, mgr.getWorkflow(wf.getId()).getStatus());

        mgr.executeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.RUNNING, mgr.getWorkflow(wf.getId()).getStatus());

        // Complete successfully
        mgr.completeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.COMPLETED, mgr.getWorkflow(wf.getId()).getStatus());
    }

    @Test
    @DisplayName("pause-reset lifecycle: DRAFT → RUNNING → PAUSED → DRAFT → RUNNING")
    void pauseAndReset() {
        WorkflowManager mgr = new WorkflowManager();
        var wf = createSimpleWorkflow(mgr);

        mgr.executeWorkflow(wf.getId());
        mgr.pauseWorkflow(wf.getId());
        mgr.resetWorkflow(wf.getId());

        assertEquals(WorkflowStatus.DRAFT, mgr.getWorkflow(wf.getId()).getStatus());

        mgr.executeWorkflow(wf.getId());
        assertEquals(WorkflowStatus.RUNNING, mgr.getWorkflow(wf.getId()).getStatus());
    }

    // ──── Helpers ───────────────────────────────────────────────────────────

    private com.doe.core.model.Workflow createSimpleWorkflow(WorkflowManager mgr) {
        var j = com.doe.core.model.Job.newJob("test").timeoutMs(60000L).build();
        var wj = com.doe.core.model.WorkflowJob.fromJob(j)
                .dagIndex(0)
                .dependencies(List.of())
                .build();
        var wf = com.doe.core.model.Workflow.newWorkflow("test")
                .addJob(wj)
                .build();
        return mgr.registerWorkflow(wf);
    }

    private record Transition(WorkflowStatus from, WorkflowStatus to) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Transition that)) return false;
            return from == that.from && to == that.to;
        }

        @Override
        public int hashCode() {
            return 31 * from.hashCode() + to.hashCode();
        }
    }
}
