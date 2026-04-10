package com.doe.manager.recovery;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import com.doe.manager.scheduler.JobQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StartupRecoveryService}.
 * <p>
 * All dependencies are mocked — no Spring context or DB needed.
 */
@ExtendWith(MockitoExtension.class)
class StartupRecoveryServiceTest {

    @Mock JobRepository jobRepository;
    @Mock WorkerRepository workerRepository;
    @Mock JobQueue jobQueue;

    StartupRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new StartupRecoveryService(jobRepository, workerRepository, jobQueue);
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private JobEntity makeEntity(UUID id, JobStatus status) {
        JobEntity e = new JobEntity(id, status, "{\"type\":\"test\"}", Instant.now(), Instant.now());
        if (status == JobStatus.ASSIGNED) e.setWorkerId(UUID.randomUUID());
        return e;
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ASSIGNED and RUNNING jobs are reset in DB to PENDING")
    void recover_resetsOrphansInDb() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        JobEntity assigned = makeEntity(id1, JobStatus.ASSIGNED);
        JobEntity running  = makeEntity(id2, JobStatus.RUNNING);

        when(workerRepository.updateAllStatuses(WorkerStatus.OFFLINE)).thenReturn(2);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of(assigned, running));
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(assigned, running));

        service.recover();

        // DB mutations
        assertEquals(JobStatus.PENDING, assigned.getStatus());
        assertNull(assigned.getWorkerId());
        assertEquals(JobStatus.PENDING, running.getStatus());
        verify(jobRepository, times(2)).save(any());

        // Both enqueued (via PENDING load step)
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobQueue, times(2)).enqueue(captor.capture());
        List<Job> enqueued = captor.getAllValues();
        assertTrue(enqueued.stream().allMatch(j -> j.getStatus() == JobStatus.PENDING));
        assertTrue(enqueued.stream().anyMatch(j -> j.getId().equals(id1)));
        assertTrue(enqueued.stream().anyMatch(j -> j.getId().equals(id2)));
    }

    @Test
    @DisplayName("All workers are bulk-set to OFFLINE on every startup")
    void recover_marksAllWorkersOffline() {
        when(workerRepository.updateAllStatuses(WorkerStatus.OFFLINE)).thenReturn(3);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of());

        service.recover();

        verify(workerRepository).updateAllStatuses(WorkerStatus.OFFLINE);
    }

    @Test
    @DisplayName("Orphan query uses ASSIGNED and RUNNING as filter statuses")
    @SuppressWarnings("unchecked")
    void recover_queriesCorrectStatuses() {
        when(workerRepository.updateAllStatuses(any())).thenReturn(0);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of());

        service.recover();

        ArgumentCaptor<Collection<JobStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(jobRepository).findByStatusIn(captor.capture());
        Collection<JobStatus> queried = captor.getValue();
        assertTrue(queried.contains(JobStatus.ASSIGNED));
        assertTrue(queried.contains(JobStatus.RUNNING));
        assertFalse(queried.contains(JobStatus.PENDING));
        assertFalse(queried.contains(JobStatus.COMPLETED));
        assertFalse(queried.contains(JobStatus.FAILED));
    }

    @Test
    @DisplayName("Pre-existing PENDING jobs (not orphans) are loaded into the queue on startup")
    void recover_preExistingPending_areEnqueued() {
        UUID id = UUID.randomUUID();
        JobEntity pending = makeEntity(id, JobStatus.PENDING);

        when(workerRepository.updateAllStatuses(any())).thenReturn(0);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of()); // no orphans
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(pending));

        service.recover();

        // No DB save (job was already PENDING — no mutation needed)
        verify(jobRepository, never()).save(any());

        // But it must be enqueued
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobQueue).enqueue(captor.capture());
        assertEquals(id, captor.getValue().getId());
        assertEquals(JobStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("No enqueue and no save when DB has no jobs at all")
    void recover_emptyDb_noEnqueueNoSave() {
        when(workerRepository.updateAllStatuses(any())).thenReturn(0);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of());
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of());

        service.recover();

        verify(jobQueue, never()).enqueue(any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("retryCount from DB is preserved in the reconstructed Job")
    void recover_preservesRetryCount() {
        UUID id = UUID.randomUUID();
        JobEntity entity = makeEntity(id, JobStatus.RUNNING);
        entity.setRetryCount(2);

        when(workerRepository.updateAllStatuses(any())).thenReturn(0);
        when(jobRepository.findByStatusIn(anyCollection())).thenReturn(List.of(entity));
        // After reset, entity is PENDING — return it from the PENDING query
        when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(entity));

        service.recover();

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobQueue).enqueue(captor.capture());
        assertEquals(2, captor.getValue().getRetryCount());
    }
}
