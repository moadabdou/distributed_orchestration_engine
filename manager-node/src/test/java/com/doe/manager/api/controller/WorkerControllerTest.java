package com.doe.manager.api.controller;

import com.doe.core.model.WorkerStatus;
import com.doe.manager.api.dto.WorkerResponse;
import com.doe.manager.api.service.WorkerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkerController.class)
public class WorkerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkerService workerService;

    @Test
    void listWorkers_ReturnsListOfWorkers() throws Exception {
        UUID workerId = UUID.randomUUID();
        WorkerResponse mockResponse = new WorkerResponse(
                workerId, "worker-1", "127.0.0.1", WorkerStatus.BUSY, Instant.now()
        );

        Mockito.when(workerService.listWorkers()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/v1/workers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(workerId.toString()))
                .andExpect(jsonPath("$[0].hostname").value("worker-1"))
                .andExpect(jsonPath("$[0].status").value("BUSY"));
    }
}
