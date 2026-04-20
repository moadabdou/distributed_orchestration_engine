package com.doe.manager.api.controller;

import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.api.dto.*;
import com.doe.manager.api.service.WorkflowService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for {@link WorkflowController}.
 *
 * <p>Uses {@code @WebMvcTest} so the Spring MVC slice is loaded with a mocked
 * {@link WorkflowService} — no database, no Testcontainers required.
 */
@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowService workflowService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WorkflowResponse stubWorkflow(UUID id, WorkflowStatus status, int totalJobs) {
        return new WorkflowResponse(
                id, "test-workflow", status,
                totalJobs, 0, 0, totalJobs,
                Instant.now(), Instant.now()
        );
    }

    // ── POST /api/v1/workflows ────────────────────────────────────────────────

    @Test
    void createWorkflow_Returns201WithBody() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.DRAFT, 2);
        Mockito.when(workflowService.createWorkflow(any(CreateWorkflowRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "test-workflow",
                                  "jobs": [
                                    {"label":"A","payload":"task-A"},
                                    {"label":"B","payload":"task-B"}
                                  ],
                                  "dependencies": [{"fromJobLabel":"A","toJobLabel":"B"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("test-workflow"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalJobs").value(2));
    }

    // ── GET /api/v1/workflows ────────────────────────────────────────────────

    @Test
    void listWorkflows_Returns200WithPaginatedContent() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowSummaryResponse summary = new WorkflowSummaryResponse(
                id, "test-workflow", WorkflowStatus.DRAFT, 3, Instant.now()
        );
        Page<WorkflowSummaryResponse> page = new PageImpl<>(
                List.of(summary), PageRequest.of(0, 20), 1
        );
        Mockito.when(workflowService.listWorkflows(0, 20, null)).thenReturn(page);

        mockMvc.perform(get("/api/v1/workflows")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].name").value("test-workflow"))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listWorkflows_WithStatusFilter_PassesStatusToService() throws Exception {
        Page<WorkflowSummaryResponse> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        Mockito.when(workflowService.listWorkflows(0, 20, WorkflowStatus.RUNNING)).thenReturn(empty);

        mockMvc.perform(get("/api/v1/workflows")
                        .param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ── GET /api/v1/workflows/{id} ───────────────────────────────────────────

    @Test
    void getWorkflow_Returns200WithDetail() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.RUNNING, 4);
        Mockito.when(workflowService.getWorkflow(id)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/workflows/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.totalJobs").value(4));
    }

    // ── PUT /api/v1/workflows/{id} ───────────────────────────────────────────

    @Test
    void updateWorkflow_Returns200WithUpdatedBody() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.DRAFT, 1);
        Mockito.when(workflowService.updateWorkflow(eq(id), any(UpdateWorkflowRequest.class))).thenReturn(resp);

        mockMvc.perform(put("/api/v1/workflows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "updated-workflow",
                                  "jobs": [{"label":"A","payload":"task-A"}],
                                  "dependencies": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.totalJobs").value(1));
    }

    // ── DELETE /api/v1/workflows/{id} ────────────────────────────────────────

    @Test
    void deleteWorkflow_Returns204() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doNothing().when(workflowService).deleteWorkflow(id);

        mockMvc.perform(delete("/api/v1/workflows/{id}", id))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/workflows/{id}/execute ──────────────────────────────────

    @Test
    void executeWorkflow_Returns200WithRunningStatus() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.RUNNING, 2);
        Mockito.when(workflowService.executeWorkflow(id)).thenReturn(resp);

        mockMvc.perform(post("/api/v1/workflows/{id}/execute", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    // ── POST /api/v1/workflows/{id}/pause ────────────────────────────────────

    @Test
    void pauseWorkflow_Returns200WithPausedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.PAUSED, 2);
        Mockito.when(workflowService.pauseWorkflow(id)).thenReturn(resp);

        mockMvc.perform(post("/api/v1/workflows/{id}/pause", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    // ── POST /api/v1/workflows/{id}/resume ───────────────────────────────────

    @Test
    void resumeWorkflow_Returns200WithRunningStatus() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.RUNNING, 2);
        Mockito.when(workflowService.resumeWorkflow(id)).thenReturn(resp);

        mockMvc.perform(post("/api/v1/workflows/{id}/resume", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    // ── POST /api/v1/workflows/{id}/reset ────────────────────────────────────

    @Test
    void resetWorkflow_Returns200WithDraftStatus() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowResponse resp = stubWorkflow(id, WorkflowStatus.DRAFT, 2);
        Mockito.when(workflowService.resetWorkflow(id)).thenReturn(resp);

        mockMvc.perform(post("/api/v1/workflows/{id}/reset", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ── GET /api/v1/workflows/{id}/dag ───────────────────────────────────────

    @Test
    void getDag_Returns200WithNodesAndEdges() throws Exception {
        UUID id = UUID.randomUUID();
        UUID jobA = UUID.randomUUID();
        UUID jobB = UUID.randomUUID();

        DagGraphResponse dag = new DagGraphResponse(
                id, "test-workflow", WorkflowStatus.DRAFT,
                List.of(
                        new DagGraphResponse.DagNodeResponse(
                                jobA, "job-0", 0, JobStatus.PENDING, "task-A",
                                null, jobB, 0, null, Instant.now(), Instant.now()
                        ),
                        new DagGraphResponse.DagNodeResponse(
                                jobB, "job-1", 1, JobStatus.PENDING, "task-B",
                                null, jobB, 0, null, Instant.now(), Instant.now()
                        )
                ),
                List.of(new DagGraphResponse.DagEdgeResponse(jobA, jobB))
        );

        Mockito.when(workflowService.getDag(id)).thenReturn(dag);

        mockMvc.perform(get("/api/v1/workflows/{id}/dag", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value(id.toString()))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].sourceJobId").value(jobA.toString()))
                .andExpect(jsonPath("$.edges[0].targetJobId").value(jobB.toString()));
    }
    // ── DELETE /api/v1/workflows/{id}/xcom ──────────────────────────────────
    @Test
    void clearXComHistory_Returns204() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doNothing().when(workflowService).clearXComHistory(id);

        mockMvc.perform(delete("/api/v1/workflows/{id}/xcom", id))
                .andExpect(status().isNoContent());
    }
}

