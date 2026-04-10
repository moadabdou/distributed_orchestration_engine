package com.doe.manager.persistence;

import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkerEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatabaseEventListener}.
 * <p>
 * Verifies that DB writes are correctly triggered for each engine event.
 * Uses Mockito for the repositories — no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseEventListenerTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private com.doe.core.registry.WorkerRegistry workerRegistry;

    private DatabaseEventListener listener;

    @BeforeEach
    void setUp() {
        // Mock the TransactionTemplate to execute the callback immediately
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        listener = new DatabaseEventListener(workerRepository, jobRepository, transactionTemplate, workerRegistry);
        // Manually invoke the @PostConstruct callback
        listener.init();
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
    }

    // ─── Worker events ───────────────────────────────────────────────────────

    @Test
    @DisplayName("onWorkerRegistered → inserts a new WorkerEntity with IDLE status")
    void onWorkerRegistered_insertsEntity() {
        UUID workerId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onWorkerRegistered(workerId, "host-1", "10.0.0.1", 4, now);

        ArgumentCaptor<WorkerEntity> captor = ArgumentCaptor.forClass(WorkerEntity.class);
        verify(workerRepository).save(captor.capture());
        WorkerEntity saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(workerId);
        assertThat(saved.getHostname()).isEqualTo("host-1");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getStatus()).isEqualTo(WorkerStatus.ONLINE);
        assertThat(saved.getRegisteredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("onWorkerDied → sets worker status to OFFLINE")
    void onWorkerDied_setsOffline() {
        UUID workerId = UUID.randomUUID();
        WorkerEntity entity = new WorkerEntity(workerId, "host", "ip", WorkerStatus.ONLINE, 4, Instant.now(), Instant.now());
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(entity));

        listener.onWorkerDied(workerId);

        assertThat(entity.getStatus()).isEqualTo(WorkerStatus.OFFLINE);
        verify(workerRepository).save(entity);
    }

    @Test
    @DisplayName("onWorkerHeartbeat → accumulates in buffer, NOT immediately sent to DB")
    void onWorkerHeartbeat_bufferedNotImmediate() {
        UUID workerId = UUID.randomUUID();
        listener.onWorkerHeartbeat(workerId, Instant.now());

        // Should NOT have called the repository yet (buffered)
        verifyNoInteractions(workerRepository);
    }

    @Test
    @DisplayName("flushHeartbeats → calls updateHeartbeat for accumulated heartbeats")
    void flushHeartbeats_writesToDb() {
        UUID workerId = UUID.randomUUID();
        Instant ts = Instant.now();
        listener.onWorkerHeartbeat(workerId, ts);

        // Manually invoke the flush (normally done by the background scheduler)
        listener.flushHeartbeats();

        verify(workerRepository).updateHeartbeat(workerId, ts);
    }

    @Test
    @DisplayName("flushHeartbeats with no pending heartbeats → no DB call")
    void flushHeartbeats_noPending_noDbCall() {
        listener.flushHeartbeats();
        verifyNoInteractions(workerRepository);
    }

    // ─── Job events ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("onJobAssigned → updates job status=ASSIGNED and workerId")
    void onJobAssigned_updatesEntity() {
        UUID jobId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Instant now = Instant.now();
        JobEntity entity = jobEntity(jobId, JobStatus.PENDING);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(entity));

        listener.onJobAssigned(jobId, workerId, now);

        assertThat(entity.getStatus()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(entity.getWorkerId()).isEqualTo(workerId);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        verify(jobRepository).save(entity);
    }

    @Test
    @DisplayName("onJobRunning → updates job status=RUNNING")
    void onJobRunning_updatesEntity() {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        JobEntity entity = jobEntity(jobId, JobStatus.ASSIGNED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(entity));

        listener.onJobRunning(jobId, now);

        assertThat(entity.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        verify(jobRepository).save(entity);
    }

    @Test
    @DisplayName("onJobCompleted → updates job status=COMPLETED and result")
    void onJobCompleted_updatesEntity() {
        UUID jobId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Instant now = Instant.now();
        JobEntity entity = jobEntity(jobId, JobStatus.RUNNING);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(entity));

        listener.onJobCompleted(jobId, workerId, "done", now);

        assertThat(entity.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(entity.getResult()).isEqualTo("done");
        assertThat(entity.getWorkerId()).isEqualTo(workerId);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        verify(jobRepository).save(entity);
    }

    @Test
    @DisplayName("onJobFailed → updates job status=FAILED and result")
    void onJobFailed_updatesEntity() {
        UUID jobId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Instant now = Instant.now();
        JobEntity entity = jobEntity(jobId, JobStatus.RUNNING);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(entity));

        listener.onJobFailed(jobId, workerId, "boom", now);

        assertThat(entity.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(entity.getResult()).isEqualTo("boom");
        assertThat(entity.getWorkerId()).isEqualTo(workerId);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        verify(jobRepository).save(entity);
    }

    @Test
    @DisplayName("onJobRequeued → status=PENDING, workerId=null, retryCount updated")
    void onJobRequeued_clearWorkerAndSetPending() {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        JobEntity entity = jobEntity(jobId, JobStatus.ASSIGNED);
        entity.setWorkerId(UUID.randomUUID()); // simulate it had a worker
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(entity));

        listener.onJobRequeued(jobId, 2, now);

        assertThat(entity.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(entity.getWorkerId()).isNull();
        assertThat(entity.getRetryCount()).isEqualTo(2);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        verify(jobRepository).save(entity);
    }

    @Test
    @DisplayName("job event with unknown ID → logs warning, no save called")
    void jobEvent_unknownId_noSave() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        listener.onJobAssigned(jobId, UUID.randomUUID(), Instant.now());

        verify(jobRepository, never()).save(any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JobEntity jobEntity(UUID id, JobStatus status) {
        return new JobEntity(id, status, "{}", Instant.now(), Instant.now());
    }
}
