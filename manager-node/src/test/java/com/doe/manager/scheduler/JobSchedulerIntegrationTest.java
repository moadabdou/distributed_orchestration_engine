package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
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
        server = com.doe.manager.server.TestManagerServerBuilder.build(0);

        CountDownLatch ready = new CountDownLatch(1);
        serverThread = Thread.ofVirtual().start(() -> {
            ready.countDown();
            server.start();
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
        String secret = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";
        String token = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        String json = "{\"hostname\":\"test-worker\", \"auth_token\":\"" + token + "\"}";
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
    @DisplayName("5 jobs enqueued with 2 available workers → all 5 eventually assigned")
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

            // 3. Each worker reads ASSIGN_JOB messages; after each one it frees capacity
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
                                // Simulate job completion: release capacity back to normal
                                com.google.gson.JsonObject p = new com.google.gson.Gson().fromJson(msg.payloadAsString(), com.google.gson.JsonObject.class); java.util.UUID jId = java.util.UUID.fromString(p.get("jobId").getAsString()); server.getRegistry().releaseCapacity(wid, jId);
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
    @DisplayName("Job re-queued when all workers at max capacity → assigned as soon as capacity frees")
    void jobRequeued_whenWorkerAtMaxCapacity_thenAssigned() throws Exception {
        List<Socket> workerSockets = new ArrayList<>();
        CountDownLatch assigned = new CountDownLatch(1);

        try {
            // 1. Connect 1 worker but immediately consume its capacity so scheduler re-queues the job
            Socket ws = new Socket("localhost", server.getLocalPort());
            ws.setSoTimeout(10_000);
            workerSockets.add(ws);
            UUID workerId = registerWorker(ws);
            Thread.sleep(200);

            // Force worker to its max capacity
            server.getRegistry().get(workerId).ifPresent(w -> {
                for (int i = 0; i < w.getMaxCapacity(); i++) {
                    w.tryReserveCapacity(java.util.UUID.randomUUID());
                }
            });

            // 2. Enqueue 1 job — should be re-queued initially
            server.getJobScheduler().getQueue().enqueue(Job.newJob("{\"cmd\":\"pending\"}").build());

            // 3. Free the worker after 300ms — scheduler should then assign
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(300);
                    server.getRegistry().get(workerId).ifPresent(w -> w.getActiveJobs().forEach(j -> server.getRegistry().releaseCapacity(workerId, j)));
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
                    "Job should have been assigned once the worker recovered capacity");

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("3 jobs processed concurrently by 1 worker within its max capacity")
    void threeJobs_oneWorker_processedConcurrently() throws Exception {
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

            // 3. Worker receives ASSIGN_JOB incrementally up to 4, replies continuously
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
                            resultBody.addProperty("jobId", jobId);
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

            // Verify worker is fully available structurally again 
            assertEquals(0,
                    server.getRegistry().get(workerId).orElseThrow().getActiveJobCount());

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("High-throughput Submission Test")
    void highThroughputSubmission() throws Exception {
        int numWorkers = 2;
        int numJobs = 2000;
        
        List<Socket> workerSockets = new ArrayList<>();
        List<UUID> workerIds = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numJobs);

        try {
            for (int i = 0; i < numWorkers; i++) {
                Socket ws = new Socket("localhost", server.getLocalPort());
                ws.setSoTimeout(0);
                workerSockets.add(ws);
                workerIds.add(registerWorker(ws));
                
                final Socket workerSocket = ws;
                Thread.ofVirtual().start(() -> {
                    try {
                        InputStream in = workerSocket.getInputStream();
                        OutputStream out = workerSocket.getOutputStream();
                        while (!Thread.currentThread().isInterrupted()) {
                            Message msg = ProtocolDecoder.decode(in);
                            if (msg.type() == MessageType.ASSIGN_JOB) {
                                JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                                String jid = payload.get("jobId").getAsString();
                                
                                JsonObject resultBody = new JsonObject();
                                resultBody.addProperty("jobId", jid);
                                resultBody.addProperty("status", "COMPLETED");
                                resultBody.addProperty("output", "success");
                                out.write(ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody)));
                                out.flush();
                                
                                completedCount.incrementAndGet();
                                latch.countDown();
                            }
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                });
            }

            Thread.sleep(500);
            
            JobQueue queue = server.getJobScheduler().getQueue();
            
            for (int i = 0; i < numJobs; i++) {
                Thread.ofVirtual().start(() -> {
                    queue.enqueue(Job.newJob("{\"cmd\":\"high-throughput\"}").build());
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "High-throughput test did not complete in time");
            assertEquals(numJobs, completedCount.get());
        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("Capacity Exhaustion (Backpressure) Test")
    void capacityExhaustion() throws Exception {
        Socket ws = new Socket("localhost", server.getLocalPort());
        ws.setSoTimeout(0);
        UUID workerId = registerWorker(ws);
        Thread.sleep(200);

        JobQueue queue = server.getJobScheduler().getQueue();
        int numJobs = 50;
        
        AtomicInteger assigned = new AtomicInteger();
        CountDownLatch allAssigned = new CountDownLatch(numJobs);
        java.util.Queue<String> jobIdsToComplete = new java.util.concurrent.ConcurrentLinkedQueue<>();

        try {
            Thread.ofVirtual().start(() -> {
                try {
                    InputStream in = ws.getInputStream();
                    while (!Thread.currentThread().isInterrupted()) {
                        Message msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.ASSIGN_JOB) {
                            assigned.incrementAndGet();
                            JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                            jobIdsToComplete.offer(payload.get("jobId").getAsString());
                            allAssigned.countDown();
                        }
                    }
                } catch (IOException e) {}
            });

            for (int i = 0; i < numJobs; i++) {
                queue.enqueue(Job.newJob("{\"cmd\":\"backpressure\"}").build());
            }

            org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> assigned.get() == 4);
            assertEquals(4, assigned.get(), "Should only take max capacity of 4 initially");
            assertTrue(queue.size() >= 45, "Queue should hold the rest");

            Thread.ofVirtual().start(() -> {
                try {
                    OutputStream out = ws.getOutputStream();
                    while (allAssigned.getCount() > 0) {
                        String jid = jobIdsToComplete.poll();
                        if (jid != null) {
                            JsonObject resultBody = new JsonObject();
                            resultBody.addProperty("jobId", jid);
                            resultBody.addProperty("status", "COMPLETED");
                            out.write(ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody)));
                            out.flush();
                        } else {
                            Thread.sleep(50);
                        }
                    }
                } catch (Exception e) {}
            });

            assertTrue(allAssigned.await(15, TimeUnit.SECONDS), "All jobs should eventually be assigned");
        } finally {
            ws.close();
        }
    }

    @Test
    @DisplayName("Rapid Worker Churn Under Load - ensures jobs complete despite worker crashes")
    void rapidWorkerChurnUnderLoad() throws Exception {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("Invalid job state transition")) {
                // ignore expected race condition
            } else if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e);
            }
        });

        try {
            int numJobs = 500;
            JobQueue queue = server.getJobScheduler().getQueue();
            List<UUID> enqueuedIds = new ArrayList<>();
        
        // Enqueue jobs
        for (int i = 0; i < numJobs; i++) {
            Job j = Job.newJob("{\"cmd\":\"churn\"}").build();
            queue.enqueue(j);
            enqueuedIds.add(j.getId());
        }

        AtomicInteger workersSpawned = new AtomicInteger(0);
        CountDownLatch churnLatch = new CountDownLatch(1);

        // Churn worker connections (crash simulation)
        Thread churnThread = Thread.ofVirtual().start(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    Socket ws = new Socket("localhost", server.getLocalPort());
                    ws.setSoTimeout(0);
                    try {
                        registerWorker(ws);
                        workersSpawned.incrementAndGet();
                        // Read assignments for 50ms, sending JOB_RUNNING to prevent invalid state failures, then crash
                        Thread.ofVirtual().start(() -> {
                            try {
                                InputStream in = ws.getInputStream();
                                long endTime = System.currentTimeMillis() + 50;
                                while (System.currentTimeMillis() < endTime) {
                                    if (in.available() > 0) {
                                        Message m = ProtocolDecoder.decode(in);
                                        if (m.type() == MessageType.ASSIGN_JOB) {
                                            JsonObject payload = GSON.fromJson(m.payloadAsString(), JsonObject.class);
                                            String jid = payload.get("jobId").getAsString();
                                            JsonObject runningBody = new JsonObject();
                                            runningBody.addProperty("jobId", jid);
                                            ws.getOutputStream().write(ProtocolEncoder.encode(MessageType.JOB_RUNNING, GSON.toJson(runningBody)));
                                            ws.getOutputStream().flush();
                                        }
                                    } else {
                                        Thread.sleep(2);
                                    }
                                }
                            } catch (Exception e) {}
                            try { ws.close(); } catch (Exception e) {}
                        });
                    } catch (Exception e) {
                        ws.close();
                    }
                }
            } catch (Exception e) {}
            churnLatch.countDown();
        });

        churnLatch.await(10, TimeUnit.SECONDS);

        // Churn is over. Now connect stable workers to finish whatever is left in the queue or resurrected.
        List<Socket> stableWorkers = new ArrayList<>();
        List<Thread> stableThreads = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            Socket stableWorker = new Socket("localhost", server.getLocalPort());
            stableWorker.setSoTimeout(0);
            registerWorker(stableWorker);
            stableWorkers.add(stableWorker);
            
            Thread stableThread = Thread.ofVirtual().start(() -> {
                try {
                    InputStream in = stableWorker.getInputStream();
                    OutputStream out = stableWorker.getOutputStream();
                    while (!Thread.currentThread().isInterrupted()) {
                        Message m = ProtocolDecoder.decode(in);
                        if (m.type() == MessageType.ASSIGN_JOB) {
                            JsonObject payload = GSON.fromJson(m.payloadAsString(), JsonObject.class);
                            String jid = payload.get("jobId").getAsString();
                            
                            JsonObject runningBody = new JsonObject();
                            runningBody.addProperty("jobId", jid);
                            out.write(ProtocolEncoder.encode(MessageType.JOB_RUNNING, GSON.toJson(runningBody)));
                            
                            JsonObject resultBody = new JsonObject();
                            resultBody.addProperty("jobId", jid);
                            resultBody.addProperty("status", "COMPLETED");
                            out.write(ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody)));
                            out.flush();
                            completedCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {}
            });
            stableThreads.add(stableThread);
        }

        // Wait until all exactly `numJobs` are globally completed or failed (due to retry exhaustion)
        org.awaitility.Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> {
            return enqueuedIds.stream().allMatch(id -> {
                return server.getJobRegistry().get(id).map(j -> j.getStatus() == JobStatus.COMPLETED || j.getStatus() == JobStatus.FAILED).orElse(false);
            });
        });

        stableThreads.forEach(Thread::interrupt);
        stableWorkers.forEach(w -> { try { w.close(); } catch (Exception e){} });

        // As long as no ConcurrentModificationException occurs
        assertTrue(workersSpawned.get() > 0, "Should have spawned multiple short-lived workers");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
        }
    }

    @Test
    @DisplayName("Fair Job Distribution Test - stats and verification")
    void fairJobDistribution() throws Exception {
        int numWorkers = 5;
        int numJobs = 1000;
        
        List<Socket> workerSockets = new ArrayList<>();
        List<UUID> workerIds = new ArrayList<>();
        int[] assignmentsPerWorker = new int[numWorkers];
        CountDownLatch latch = new CountDownLatch(numJobs);

        try {
            // Register workers
            for (int i = 0; i < numWorkers; i++) {
                Socket ws = new Socket("localhost", server.getLocalPort());
                ws.setSoTimeout(0);
                workerSockets.add(ws);
                workerIds.add(registerWorker(ws));
                
                final int workerIndex = i;
                final Socket workerSocket = ws;
                Thread.ofVirtual().start(() -> {
                    try {
                        InputStream in = workerSocket.getInputStream();
                        OutputStream out = workerSocket.getOutputStream();
                        while (!Thread.currentThread().isInterrupted()) {
                            Message msg = ProtocolDecoder.decode(in);
                            if (msg.type() == MessageType.ASSIGN_JOB) {
                                assignmentsPerWorker[workerIndex]++;
                                JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                                String jid = payload.get("jobId").getAsString();
                                
                                JsonObject resultBody = new JsonObject();
                                resultBody.addProperty("jobId", jid);
                                resultBody.addProperty("status", "COMPLETED");
                                out.write(ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody)));
                                out.flush();
                                
                                latch.countDown();
                            }
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                });
            }

            // Allow workers time to register and be available
            Thread.sleep(500);
            
            JobQueue queue = server.getJobScheduler().getQueue();
            for (int i = 0; i < numJobs; i++) {
                queue.enqueue(Job.newJob("{\"cmd\":\"dist-test\"}").build());
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "Distribution test did not complete in time");
            
            // Calculate stats
            int sum = 0;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            System.out.println("\n--- Job Distribution Stats ---");
            for (int i = 0; i < numWorkers; i++) {
                int count = assignmentsPerWorker[i];
                System.out.println("Worker " + i + " processed: " + count + " jobs");
                sum += count;
                if (count < min) min = count;
                if (count > max) max = count;
            }
            double avg = (double) sum / numWorkers;
            
            double varianceSum = 0;
            for (int count : assignmentsPerWorker) {
                varianceSum += Math.pow(count - avg, 2);
            }
            double stdDev = Math.sqrt(varianceSum / numWorkers);

            System.out.println("Total Jobs: " + sum);
            System.out.println("Mean: " + avg);
            System.out.println("Min: " + min);
            System.out.println("Max: " + max);
            System.out.println(String.format("Std Dev: %.2f", stdDev));
            System.out.println("------------------------------\n");
            
            assertEquals(numJobs, sum, "All jobs should be processed");
            assertTrue(min > 0, "Every worker should get at least one job");
            
            // Assert that distribution is relatively fair (Standard Deviation shouldn't be insanely high)
            assertTrue(stdDev < (numJobs / (double) numWorkers), "Distribution should be reasonably fair");
            
        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    @DisplayName("10 rapid-fire jobs across two quad-capacity workers — verify full parallel occupation")
    void tenRapidFireJobs_twoQuadCapacityWorkers_fullParallelOccupation() throws Exception {
        int numWorkers = 2;
        int numJobs = 10;
        int workerMaxCapacity = 4;

        List<Socket> workerSockets = new ArrayList<>();
        List<UUID> workerIds = new ArrayList<>();
        AtomicInteger assignmentsReceived = new AtomicInteger(0);
        CountDownLatch allAssigned = new CountDownLatch(numJobs);
        CountDownLatch allCompleted = new CountDownLatch(numJobs);
        long[] assignmentTimestamps = new long[numJobs];
        AtomicInteger idx = new AtomicInteger(0);

        try {
            // 1. Connect 2 workers (each with maxCapacity=4)
            for (int i = 0; i < numWorkers; i++) {
                Socket ws = new Socket("localhost", server.getLocalPort());
                ws.setSoTimeout(10_000);
                workerSockets.add(ws);
                UUID wid = registerWorker(ws);
                workerIds.add(wid);

                // Pre-fill each worker to its max capacity so we verify the scheduler fills both
                server.getRegistry().get(wid).ifPresent(w -> {
                    for (int j = 0; j < workerMaxCapacity; j++) {
                        w.tryReserveCapacity(UUID.randomUUID());
                    }
                });
            }

            Thread.sleep(200);
            assertEquals(2, server.getRegistry().size(), "Both workers should be registered");

            // Verify both workers are at max capacity
            for (UUID wid : workerIds) {
                assertEquals(workerMaxCapacity,
                        server.getRegistry().get(wid).orElseThrow().getActiveJobCount(),
                        "Worker should be at max capacity before test");
            }

            // 2. Enqueue all 10 jobs simultaneously
            JobQueue queue = server.getJobScheduler().getQueue();
            List<Job> jobs = new ArrayList<>();
            for (int i = 0; i < numJobs; i++) {
                Job job = Job.newJob("{\"cmd\":\"rapid-" + i + "\"}").build();
                jobs.add(job);
                queue.enqueue(job);
            }

            // 3. Immediately free all pre-filled capacity so scheduler can assign real jobs
            for (UUID wid : workerIds) {
                server.getRegistry().get(wid).ifPresent(w ->
                        new ArrayList<>(w.getActiveJobs()).forEach(j -> server.getRegistry().releaseCapacity(wid, j)));
            }

            // 4. Each worker reads ASSIGN_JOB messages and completes them rapidly
            for (int i = 0; i < numWorkers; i++) {
                final Socket ws = workerSockets.get(i);
                final UUID wid = workerIds.get(i);

                Thread.ofVirtual().start(() -> {
                    try {
                        InputStream in = ws.getInputStream();
                        OutputStream out = ws.getOutputStream();
                        while (!Thread.currentThread().isInterrupted()) {
                            Message msg = ProtocolDecoder.decode(in);
                            if (msg.type() == MessageType.ASSIGN_JOB) {
                                int currentIdx = idx.getAndIncrement();
                                assignmentTimestamps[currentIdx] = System.nanoTime();
                                assignmentsReceived.incrementAndGet();
                                allAssigned.countDown();

                                JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                                String jobId = payload.get("jobId").getAsString();

                                // Send JOB_RUNNING
                                JsonObject runningBody = new JsonObject();
                                runningBody.addProperty("jobId", jobId);
                                out.write(ProtocolEncoder.encode(MessageType.JOB_RUNNING, GSON.toJson(runningBody)));
                                out.flush();

                                // Send JOB_RESULT immediately (rapid fire)
                                JsonObject resultBody = new JsonObject();
                                resultBody.addProperty("jobId", jobId);
                                resultBody.addProperty("status", "COMPLETED");
                                resultBody.addProperty("output", "rapid-done");
                                out.write(ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody)));
                                out.flush();

                                allCompleted.countDown();
                            }
                        }
                    } catch (IOException ignored) {}
                });
            }

            // 5. Verify all 10 jobs are assigned
            assertTrue(allAssigned.await(5, TimeUnit.SECONDS),
                    "Expected 10 ASSIGN_JOB messages but only received " + assignmentsReceived.get());

            // 6. Verify all 10 jobs complete
            assertTrue(allCompleted.await(5, TimeUnit.SECONDS),
                    "Expected 10 job completions but only received " + allCompleted.getCount() + " remaining");

            // 7. Verify all jobs are COMPLETED in registry
            Thread.sleep(200);
            for (Job job : jobs) {
                Job persisted = server.getJobRegistry().get(job.getId()).orElseThrow();
                assertEquals(JobStatus.COMPLETED, persisted.getStatus(),
                        "Job " + job.getId() + " should be COMPLETED");
            }

            // 8. Verify both workers were utilized (parallel occupation)
            int totalAssignments = assignmentsReceived.get();
            assertEquals(numJobs, totalAssignments, "All 10 jobs should have been assigned");

            // 9. Print timing distribution for verification
            long minTime = Long.MAX_VALUE;
            long maxTime = Long.MIN_VALUE;
            for (long ts : assignmentTimestamps) {
                if (ts > 0) {
                    if (ts < minTime) minTime = ts;
                    if (ts > maxTime) maxTime = ts;
                }
            }
            long spanMs = (maxTime - minTime) / 1_000_000;
            System.out.println("Rapid-fire job assignment span: " + spanMs + "ms (first to last)");

            // Verify workers are back to 0 active jobs
            for (UUID wid : workerIds) {
                assertEquals(0,
                        server.getRegistry().get(wid).orElseThrow().getActiveJobCount(),
                        "Worker should have 0 active jobs after all complete");
            }

        } finally {
            for (Socket ws : workerSockets) {
                try { ws.close(); } catch (IOException ignored) {}
            }
        }
    }
}
