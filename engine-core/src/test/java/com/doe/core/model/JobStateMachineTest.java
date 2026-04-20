package com.doe.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Job} state machine transitions.
 *
 * Covers:
 *  - All valid forward transitions
 *  - Re-queue path  (ASSIGNED → PENDING)
 *  - Invalid transitions (throw {@link IllegalStateException})
 */
class JobStateMachineTest {

    // ──── Helper ─────────────────────────────────────────────────────────────

    private Job pendingJob() {
        return Job.newJob("{\"cmd\":\"echo hello\"}").timeoutMs(60000L).build();
    }

    // ──── Valid forward transitions ──────────────────────────────────────────

    @Test
    @DisplayName("PENDING → ASSIGNED is valid")
    void transition_pendingToAssigned() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        assertEquals(JobStatus.ASSIGNED, job.getStatus());
    }

    @Test
    @DisplayName("ASSIGNED → RUNNING is valid")
    void transition_assignedToRunning() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    @DisplayName("RUNNING → COMPLETED is valid")
    void transition_runningToCompleted() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    @DisplayName("RUNNING → FAILED is valid")
    void transition_runningToFailed() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.FAILED);
        assertEquals(JobStatus.FAILED, job.getStatus());
    }

    // ──── Re-queue path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ASSIGNED → PENDING (re-queue on timeout) is valid")
    void transition_assignedToPending_requeue() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.PENDING); // re-queue
        assertEquals(JobStatus.PENDING, job.getStatus());
    }

    @Test
    @DisplayName("Re-queued job can be reassigned and completed")
    void transition_requeuedJobFullCycle() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.PENDING);   // re-queue
        job.transition(JobStatus.ASSIGNED);  // pick up again
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    // ──── Invalid transitions ────────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED → RUNNING throws IllegalStateException")
    void transition_completedToRunning_throws() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);

        assertThrows(IllegalStateException.class,
                () -> job.transition(JobStatus.RUNNING));
    }

    @Test
    @DisplayName("PENDING → RUNNING throws IllegalStateException")
    void transition_pendingToRunning_throws() {
        Job job = pendingJob();
        assertThrows(IllegalStateException.class,
                () -> job.transition(JobStatus.RUNNING));
    }

    @Test
    @DisplayName("FAILED → any status throws IllegalStateException")
    void transition_failedToAnyStatus_throws() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.FAILED);

        for (JobStatus target : JobStatus.values()) {
            if (target == JobStatus.PENDING) continue;
            assertThrows(IllegalStateException.class,
                    () -> job.transition(target),
                    "Expected exception for FAILED → " + target);
        }
    }

    @Test
    @DisplayName("COMPLETED → any status throws IllegalStateException")
    void transition_completedToAnyStatus_throws() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        job.transition(JobStatus.COMPLETED);

        for (JobStatus target : JobStatus.values()) {
            assertThrows(IllegalStateException.class,
                    () -> job.transition(target),
                    "Expected exception for COMPLETED → " + target);
        }
    }

    @Test
    @DisplayName("RUNNING → PENDING is valid (crash recovery or timeout)")
    void transition_runningToPending_valid() {
        Job job = pendingJob();
        job.transition(JobStatus.ASSIGNED);
        job.transition(JobStatus.RUNNING);
        
        job.transition(JobStatus.PENDING);
        assertEquals(JobStatus.PENDING, job.getStatus());
    }

    @Test
    @DisplayName("Null target throws NullPointerException")
    void transition_nullTarget_throwsNpe() {
        Job job = pendingJob();
        assertThrows(NullPointerException.class,
                () -> job.transition(null));
    }

    // ──── Builder & factory ──────────────────────────────────────────────────

    @Test
    @DisplayName("newJob() defaults to PENDING status and non-null id/timestamps")
    void newJob_defaults() {
        Job job = pendingJob();
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertNotNull(job.getId());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
        assertNull(job.getResult());
        assertNull(job.getAssignedWorkerId());
    }

    @Test
    @DisplayName("Builder allows setting assignedWorkerId")
    void builder_setsAssignedWorkerId() {
        UUID workerId = UUID.randomUUID();
        Job job = Job.newJob("{}").timeoutMs(60000L).assignedWorkerId(workerId).build();
        assertEquals(workerId, job.getAssignedWorkerId());
    }

    @Test
    @DisplayName("updatedAt advances after transition")
    void transition_updatesTimestamp() throws InterruptedException {
        Job job = pendingJob();
        var before = job.getUpdatedAt();
        Thread.sleep(2); // ensure clock tick
        job.transition(JobStatus.ASSIGNED);
        assertTrue(job.getUpdatedAt().isAfter(before));
    }
}
