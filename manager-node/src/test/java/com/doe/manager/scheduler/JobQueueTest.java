package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobQueue}.
 */
class JobQueueTest {

    private JobQueue queue;

    @BeforeEach
    void setUp() {
        queue = new JobQueue(null, 2);
    }

    private Job makeJob(String payload) {
        return Job.newJob(payload).timeoutMs(60000L).build();
    }

    @Test
    @DisplayName("enqueue / dequeue FIFO order")
    void enqueue_dequeue_fifo() {
        Job j1 = makeJob("a");
        Job j2 = makeJob("b");
        queue.enqueue(j1);
        queue.enqueue(j2);

        assertSame(j1, queue.dequeue());
        assertSame(j2, queue.dequeue());
        assertNull(queue.dequeue());
    }

    @Test
    @DisplayName("dequeue on empty queue returns null")
    void dequeue_empty_returnsNull() {
        assertNull(queue.dequeue());
    }

    @Test
    @DisplayName("enqueue throws JobQueueFullException when capacity is exceeded")
    void enqueue_capacityExceeded_throws() {
        queue.enqueue(makeJob("1"));
        queue.enqueue(makeJob("2"));
        assertThrows(JobQueueFullException.class, () -> queue.enqueue(makeJob("3")));
    }

    @Test
    @DisplayName("requeue throws JobQueueFullException when capacity is exceeded")
    void requeue_capacityExceeded_throws() {
        queue.enqueue(makeJob("1"));
        queue.enqueue(makeJob("2"));
        assertThrows(JobQueueFullException.class, () -> queue.requeue(makeJob("3")));
    }

    @Test
    @DisplayName("requeue puts job at the head")
    void requeue_putsJobAtHead() {
        Job j1 = makeJob("first");
        Job j2 = makeJob("second");
        queue.enqueue(j1);
        queue.requeue(j2); // j2 should be at head

        assertSame(j2, queue.dequeue());
        assertSame(j1, queue.dequeue());
    }

    @Test
    @DisplayName("size reflects enqueue/dequeue operations")
    void size_tracked() {
        assertTrue(queue.isEmpty());
        queue.enqueue(makeJob("x"));
        assertEquals(1, queue.size());
        queue.enqueue(makeJob("y"));
        assertEquals(2, queue.size());
        queue.dequeue();
        assertEquals(1, queue.size());
        queue.dequeue();
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("enqueue null throws NullPointerException")
    void enqueue_null_throws() {
        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
    }

    @Test
    @DisplayName("requeue null throws NullPointerException")
    void requeue_null_throws() {
        assertThrows(NullPointerException.class, () -> queue.requeue(null));
    }
}
