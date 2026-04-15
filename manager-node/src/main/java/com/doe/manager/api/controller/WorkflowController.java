package com.doe.manager.api.controller;

import com.doe.core.model.WorkflowStatus;
import com.doe.manager.api.dto.*;
import com.doe.manager.api.service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for workflow lifecycle management.
 *
 * <p>Base path: {@code /api/v1/workflows}
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /** POST /workflows — Create a workflow */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createWorkflow(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    /** GET /workflows — Paginated list, optional status filter */
    @GetMapping
    public Page<WorkflowSummaryResponse> listWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) WorkflowStatus status) {
        return workflowService.listWorkflows(page, size, status);
    }

    /** GET /workflows/{id} — Get workflow detail */
    @GetMapping("/{id}")
    public WorkflowResponse getWorkflow(@PathVariable UUID id) {
        return workflowService.getWorkflow(id);
    }

    /** PUT /workflows/{id} — Replace workflow definition */
    @PutMapping("/{id}")
    public WorkflowResponse updateWorkflow(
            @PathVariable UUID id,
            @RequestBody UpdateWorkflowRequest request) {
        return workflowService.updateWorkflow(id, request);
    }

    /** DELETE /workflows/{id} — Delete workflow */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkflow(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
    }

    // ── Lifecycle controls ───────────────────────────────────────────────────

    /** POST /workflows/{id}/execute — Start execution (DRAFT → RUNNING) */
    @PostMapping("/{id}/execute")
    public WorkflowResponse executeWorkflow(@PathVariable UUID id) {
        return workflowService.executeWorkflow(id);
    }

    /** POST /workflows/{id}/pause — Pause execution (RUNNING → PAUSED) */
    @PostMapping("/{id}/pause")
    public WorkflowResponse pauseWorkflow(@PathVariable UUID id) {
        return workflowService.pauseWorkflow(id);
    }

    /** POST /workflows/{id}/resume — Resume execution (PAUSED → RUNNING) */
    @PostMapping("/{id}/resume")
    public WorkflowResponse resumeWorkflow(@PathVariable UUID id) {
        return workflowService.resumeWorkflow(id);
    }

    /** POST /workflows/{id}/reset — Reset to DRAFT */
    @PostMapping("/{id}/reset")
    public WorkflowResponse resetWorkflow(@PathVariable UUID id) {
        return workflowService.resetWorkflow(id);
    }

    // ── DAG ──────────────────────────────────────────────────────────────────

    /** GET /workflows/{id}/dag — Full DAG (nodes + edges) */
    @GetMapping("/{id}/dag")
    public DagGraphResponse getDag(@PathVariable UUID id) {
        return workflowService.getDag(id);
    }
}
