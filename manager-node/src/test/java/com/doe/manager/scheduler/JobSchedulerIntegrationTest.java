package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.manager.server.ManagerServer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link JobScheduler}.
 * <p>
 * Starts a real {@link ManagerServer}, connects 2 raw-socket workers, enqueues 5 jobs,
 * and verifies that all 5 eventually receive an {@code ASSIGN_JOB} message.
 */
class JobSchedulerIntegrationTest {

    private static final Gson GSON = new Gson();

    private ManagerServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        server = new ManagerServer(0);

        CountDownLatch ready = new CountDownLatch(1);
        serverThread = Thread.ofVirtual().start(() -> {
            try {
                ready.countDown();
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Server failed to start");
        Thread.sleep(200); // let the accept-loop open
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
        serverThread.join(5_000);
    }

    // ──── Helpers ────────────────────────────────────────────────────────────

    private UUID registerWorker(Socket socket) throws IOException {
        String json = "{\"hostname\":\"test-worker\"}";
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolEncoder.encode(MessageType.REGISTER_WORKER, json.getBytes(StandardCharsets.UTF_8)));
        out.flush();

        socket.setSoTimeout(5_000);
        Message ack = ProtocolDecoder.decode(socket.getInputStream());
        assertEquals(MessageType.REGISTER_ACK, ack.type());
        JsonObject body = GSON.fromJson(ack.payloadAsString(), JsonObject.class);
        return UUID.fromString(body.get("workerId").getAsString());
    }

    // ──── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5 jobs enqueued with 2 idle workers → all 5 eventually assigned")
    void fiveJobs_twoWorkers_allAssigned() throws Exception {
        List<Socket> workerSockets = new ArrayList<>();
        List<UUID> workerIds = new ArrayList<>();
        AtomicInteger assignmentsReceived = new AtomicInteger(0);
        CountDownLatch allAssigned = new CountDownLatch(5);

        try {
            // 1. Connect 2 workers and capture their manager-assigned UUIDs
            for (int i = 0; i < 2; i++) {
                Socket ws = new Socket("localhost", server.getLocalPort());
                ws.setSoTimeout(10_000);
                workerSockets.add(ws);
                workerIds.add(registerWorker(ws));
            }

            // Give the server time to register both workers
            Thread.sleep(300);
            assertEquals(2, server.getRegistry().size(), "Both workers should be in the registry");

            // 2. Enqueue 5 jobs
            JobQueue queue = server.getJobScheduler().getQueue();
            for (int i = 0; i < 5; i++) {
                queue.enqueue(Job.newJob("{\"cmd\":\"task-" + i + "\"}").build());
            }

            // 3. Each worker reads ASSIGN_JOB messages; after each one it marks itself IDLE
            //    so the scheduler can assign the remaining jobs.
            for (int i = 0; i < workerSockets.size(); i++) {
                final Socket ws = workerSockets.get(i);
                final UUID wid = workerIds.get(i);
                Thread.ofVirtual().start(() -> {
                    try {
                        InputStream in = ws.getInputStream();
                        while (!Thread.currentThread().isInterrupted()) {
                            Message msg = ProtocolDecoder.decode(in);
                            if (msg.type() == MessageType.ASSIGN_JOB) {
                                assignmentsReceived.incrementAndGet();
                                allAssigned.countDown();
                                // Simulate job completion: release the worker
                                server.getRegistry().get(wid).ifPresent(w -> w.setIdle());
                            }
                        }
                    } catch (IOException ignored) {
                        // socket closed on shutdown — expected
                    }
                });
            }

            // 4. Wait up to 5 seconds for all 5 jobs to be assigned
            assertTrue(allAssigned.await(5, TimeUnit.SECONDS),
                    "Expected 5 ASSIGN_JOB messages but only received " + assignmentsReceived.get());

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("Job re-queued when no idle workers → assigned as soon as worker becomes idle")
    void jobRequeued_whenNoIdleWorker_thenAssigned() throws Exception {
        List<Socket> workerSockets = new ArrayList<>();
        CountDownLatch assigned = new CountDownLatch(1);

        try {
            // 1. Connect 1 worker but immediately mark it BUSY so scheduler re-queues the job
            Socket ws = new Socket("localhost", server.getLocalPort());
            ws.setSoTimeout(10_000);
            workerSockets.add(ws);
            UUID workerId = registerWorker(ws);
            Thread.sleep(200);

            // Force worker BUSY
            server.getRegistry().get(workerId).ifPresent(w -> w.trySetBusy());

            // 2. Enqueue 1 job — should be re-queued initially
            server.getJobScheduler().getQueue().enqueue(Job.newJob("{\"cmd\":\"pending\"}").build());

            // 3. Free the worker after 300ms — scheduler should then assign
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(300);
                    server.getRegistry().get(workerId).ifPresent(w -> w.setIdle());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 4. Read the assignment
            Thread.ofVirtual().start(() -> {
                try {
                    Message msg = ProtocolDecoder.decode(ws.getInputStream());
                    if (msg.type() == MessageType.ASSIGN_JOB) {
                        assigned.countDown();
                    }
                } catch (IOException ignored) {}
            });

            assertTrue(assigned.await(5, TimeUnit.SECONDS),
                    "Job should have been assigned once the worker became idle");

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("3 jobs sequentially completed by 1 worker")
    void threeJobs_oneWorker_processedSequentially() throws Exception {
        List<Socket> workerSockets = new ArrayList<>();
        CountDownLatch allCompleted = new CountDownLatch(3);

        try {
            // 1. Connect 1 worker
            Socket ws = new Socket("localhost", server.getLocalPort());
            ws.setSoTimeout(10_000);
            workerSockets.add(ws);
            UUID workerId = registerWorker(ws);
            Thread.sleep(200);

            // 2. Enqueue 3 jobs
            JobQueue queue = server.getJobScheduler().getQueue();
            List<Job> jobs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Job job = Job.newJob("{\"cmd\":\"seq-task-" + i + "\"}").build();
                jobs.add(job);
                queue.enqueue(job);
            }

            // 3. Worker reads ASSIGN_JOB, then replies with JOB_RESULT sequentially
            Thread.ofVirtual().start(() -> {
                try {
                    InputStream in = ws.getInputStream();
                    OutputStream out = ws.getOutputStream();
                    for (int i = 0; i < 3; i++) {
                        Message msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.ASSIGN_JOB) {
                            JsonObject envelope = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                            String jobId = envelope.get("jobId").getAsString();

                            // Send JOB_RUNNING first
                            JsonObject runningBody = new JsonObject();
                            runningBody.addProperty("jobId", jobId);
                            out.write(ProtocolEncoder.encode(MessageType.JOB_RUNNING, GSON.toJson(runningBody)));
                            out.flush();

                            // Simulate sending JOB_RESULT
                            JsonObject resultBody = new JsonObject();
                            resultBody.addProperty("status", "COMPLETED");
                            resultBody.addProperty("output", "success-" + i);

                            byte[] wire = ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody));
                            out.write(wire);
                            out.flush();

                            allCompleted.countDown();
                        }
                    }
                } catch (IOException ignored) {}
            });

            // 4. Wait for 3 jobs to complete
            assertTrue(allCompleted.await(5, TimeUnit.SECONDS), "Expected 3 jobs to complete");

            // Allow the manager a moment to process the final JOB_RESULT
            Thread.sleep(200);

            // 5. Verify all jobs are COMPLETED in registry and have correct output
            for (int i = 0; i < 3; i++) {
                Job job = server.getJobRegistry().get(jobs.get(i).getId()).orElseThrow();
                assertEquals(JobStatus.COMPLETED, job.getStatus());
                assertEquals("success-" + i, job.getResult());
            }

            // Verify worker is back to IDLE
            assertEquals(WorkerStatus.IDLE,
                    server.getRegistry().get(workerId).orElseThrow().getStatus());

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }
}
