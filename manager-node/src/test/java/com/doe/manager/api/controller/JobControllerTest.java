package com.doe.manager.api.controller;

import com.doe.core.model.JobStatus;
import com.doe.manager.api.dto.JobRequest;
import com.doe.manager.api.dto.JobResponse;
import com.doe.manager.api.service.JobService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
public class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @Test
    void submitJob_ReturnsCreatedJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobResponse mockResponse = new JobResponse(
                jobId, JobStatus.PENDING, "{\"cmd\":\"echo hello\"}", null, null, null, 0, Instant.now(), Instant.now()
        );

        Mockito.when(jobService.submitJob(any(JobRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"{\\\"cmd\\\":\\\"echo hello\\\"}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payload").value("{\"cmd\":\"echo hello\"}"));
    }

    @Test
    void listJobs_ReturnsPaginatedJobs() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobResponse mockResponse = new JobResponse(
                jobId, JobStatus.PENDING, "{}", null, null, null, 0, Instant.now(), Instant.now()
        );
        Page<JobResponse> page = new PageImpl<>(List.of(mockResponse), PageRequest.of(0, 20), 1);

        Mockito.when(jobService.listJobs(0, 20, JobStatus.PENDING)).thenReturn(page);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getJob_ReturnsJobDetails() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobResponse mockResponse = new JobResponse(
                jobId, JobStatus.RUNNING, "{}", "Success", UUID.randomUUID(), null , 0, Instant.now(), Instant.now()
        );

        Mockito.when(jobService.getJob(jobId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.result").value("Success"));
    }
}

