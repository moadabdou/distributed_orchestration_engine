package com.doe.manager.api.service;

import com.doe.manager.api.exception.ResourceNotFoundException;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.scheduler.DagScheduler;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.server.ManagerServer;
import com.doe.manager.workflow.WorkflowManager;
import com.doe.manager.metrics.MetricsService;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JobServiceLogTest {

    private JobService jobService;
    private final UUID jobId = UUID.randomUUID();
    private final Path logDir = Paths.get("data", "var", "logs", "jobs");
    private final Path logFile = logDir.resolve(jobId.toString() + ".log");
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(logDir);
        
        JobRepository jobRepository = Mockito.mock(JobRepository.class);
        JobQueue jobQueue = Mockito.mock(JobQueue.class);
        ManagerServer managerServer = Mockito.mock(ManagerServer.class);
        MetricsService metricsService = Mockito.mock(MetricsService.class);
        WorkflowManager workflowManager = Mockito.mock(WorkflowManager.class);
        DagScheduler dagScheduler = Mockito.mock(DagScheduler.class);

        jobService = new JobService(jobRepository, jobQueue, managerServer, metricsService, workflowManager, dagScheduler, 600000);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(logFile);
        // We don't delete the directories because other tests might need them if they run in parallel
    }

    @Test
    void testGetJobLogs_FullRange() throws IOException {
        List<String> logs = Arrays.asList("line 1", "line 2", "line 3", "line 4", "line 5");
        Files.writeString(logFile, gson.toJson(logs));

        List<String> result = jobService.getJobLogs(jobId, null, null);
        assertEquals(5, result.size());
        assertEquals("line 1", result.get(0));
        assertEquals("line 5", result.get(4));
    }

    @Test
    void testGetJobLogs_WithStart() throws IOException {
        List<String> logs = Arrays.asList("line 1", "line 2", "line 3", "line 4", "line 5");
        Files.writeString(logFile, gson.toJson(logs));

        List<String> result = jobService.getJobLogs(jobId, 2, null);
        assertEquals(3, result.size());
        assertEquals("line 3", result.get(0));
        assertEquals("line 5", result.get(2));
    }

    @Test
    void testGetJobLogs_WithLength() throws IOException {
        List<String> logs = Arrays.asList("line 1", "line 2", "line 3", "line 4", "line 5");
        Files.writeString(logFile, gson.toJson(logs));

        List<String> result = jobService.getJobLogs(jobId, null, 2);
        assertEquals(2, result.size());
        assertEquals("line 1", result.get(0));
        assertEquals("line 2", result.get(1));
    }

    @Test
    void testGetJobLogs_WithStartAndLength() throws IOException {
        List<String> logs = Arrays.asList("line 1", "line 2", "line 3", "line 4", "line 5");
        Files.writeString(logFile, gson.toJson(logs));

        List<String> result = jobService.getJobLogs(jobId, 1, 3);
        assertEquals(3, result.size());
        assertEquals("line 2", result.get(0));
        assertEquals("line 4", result.get(2));
    }

    @Test
    void testGetJobLogs_OutOfRange() throws IOException {
        List<String> logs = Arrays.asList("line 1", "line 2");
        Files.writeString(logFile, gson.toJson(logs));

        List<String> result = jobService.getJobLogs(jobId, 5, 2);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetJobLogs_FileNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> jobService.getJobLogs(UUID.randomUUID(), null, null));
    }
}
