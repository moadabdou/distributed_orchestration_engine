package com.doe.manager.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every {@link com.doe.manager.workflow.WorkflowErrorCode} variant
 * is mapped to the correct HTTP status code and error body format.
 *
 * <p>Tests the {@link com.doe.manager.api.exception.GlobalExceptionHandler} end-to-end
 * via full HTTP request/response with real persistence (Testcontainers).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowErrorHandlingTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("doe-manager")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    private static final String BASE = "/api/v1/workflows";

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createWorkflow(String body) {
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);
        System.out.println(resp.getBody()); assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    // ── Helper: standard error-body contract ──────────────────────────────────

    private void assertErrorBody(Map<String, Object> body, int expectedStatus, String expectedCode) {
        assertThat(body.get("status")).isEqualTo(expectedStatus);
        assertThat(body.get("code")).isEqualTo(expectedCode);
        assertThat(body.get("message")).isNotNull();
        assertThat(body.get("timestamp")).isNotNull();
    }

    // ── 404 — WORKFLOW_NOT_FOUND ──────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getUnknownWorkflow_Returns404WithWorkflowNotFoundCode() {
        UUID unknown = UUID.randomUUID();
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "/" + unknown, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorBody(resp.getBody(), 404, "WORKFLOW_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDagUnknownWorkflow_Returns404() {
        UUID unknown = UUID.randomUUID();
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "/" + unknown + "/dag", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorBody(resp.getBody(), 404, "WORKFLOW_NOT_FOUND");
    }

    // ── 400 — INVALID_ARGUMENT (validation) ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithBlankName_Returns400() {
        String body = """
                {"name":"","jobs":[{"label":"A","payload":"{}"}],"dependencies":[]}
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "INVALID_ARGUMENT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithNoJobs_Returns400() {
        String body = """
                {"name":"no-jobs","jobs":[],"dependencies":[]}
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "INVALID_ARGUMENT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithBlankJobPayload_Returns400() {
        String body = """
                {"name":"blank-payload","jobs":[{"label":"A","payload":""}],"dependencies":[]}
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "INVALID_ARGUMENT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithDuplicateJobLabel_Returns400() {
        String body = """
                {"name":"dup-label","jobs":[
                  {"label":"A","payload":"{}"},
                  {"label":"A","payload":"task2"}
                ],"dependencies":[]}
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "INVALID_ARGUMENT");
    }

    // ── 400 — MISSING_DEPENDENCY ──────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithUnknownDependencyLabel_Returns400WithMissingDependency() {
        String body = """
                {
                  "name":"missing-dep",
                  "jobs":[{"label":"A","payload":"task-A"}],
                  "dependencies":[{"fromJobLabel":"NONEXISTENT","toJobLabel":"A"}]
                }
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "MISSING_DEPENDENCY");
    }

    // ── 400 — DAG_HAS_CYCLE ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void createWorkflow_WithCycle_Returns400WithDagHasCycleCode() {
        // A → B → A is a cycle
        String body = """
                {
                  "name":"cycle-test",
                  "jobs":[
                    {"label":"A","payload":"task-A"},
                    {"label":"B","payload":"task-B"}
                  ],
                  "dependencies":[
                    {"fromJobLabel":"A","toJobLabel":"B"},
                    {"fromJobLabel":"B","toJobLabel":"A"}
                  ]
                }
                """;
        ResponseEntity<Map> resp = rest.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(resp.getBody(), 400, "DAG_HAS_CYCLE");
    }

    // ── 409 — WORKFLOW_ALREADY_RUNNING ────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void executeAlreadyRunningWorkflow_Returns409() {
        Map<String, Object> created = createWorkflow("""
                {"name":"already-running","jobs":[{"label":"A","payload":"{}"}],"dependencies":[]}
                """);
        UUID workflowId = UUID.fromString((String) created.get("id"));

        // First execute — OK
        rest.exchange(BASE + "/" + workflowId + "/execute",
                HttpMethod.POST, HttpEntity.EMPTY, Map.class);

        // Second execute — should conflict
        ResponseEntity<Map> resp = rest.exchange(
                BASE + "/" + workflowId + "/execute",
                HttpMethod.POST, HttpEntity.EMPTY, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertErrorBody(resp.getBody(), 409, "WORKFLOW_ALREADY_RUNNING");
    }

    // ── 409 — WORKFLOW_NOT_PAUSED ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void resumeADraftWorkflow_Returns409() {
        Map<String, Object> created = createWorkflow("""
                {"name":"resume-draft","jobs":[{"label":"A","payload":"{}"}],"dependencies":[]}
                """);
        UUID workflowId = UUID.fromString((String) created.get("id"));

        // Resume a DRAFT workflow — must fail because it is not PAUSED
        ResponseEntity<Map> resp = rest.exchange(
                BASE + "/" + workflowId + "/resume",
                HttpMethod.POST, HttpEntity.EMPTY, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertErrorBody(resp.getBody(), 409, "WORKFLOW_NOT_PAUSED");
    }

    // ── 409 — WORKFLOW_NOT_EDITABLE ───────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void updateRunningWorkflow_Returns409() {
        Map<String, Object> created = createWorkflow("""
                {"name":"update-running","jobs":[{"label":"A","payload":"{}"}],"dependencies":[]}
                """);
        UUID workflowId = UUID.fromString((String) created.get("id"));

        // Execute → RUNNING
        rest.exchange(BASE + "/" + workflowId + "/execute",
                HttpMethod.POST, HttpEntity.EMPTY, Map.class);

        // Now try to update — should conflict
        String updateBody = """
                {"name":"updated","jobs":[{"label":"X","payload":"task-X"}],"dependencies":[]}
                """;
        ResponseEntity<Map> resp = rest.exchange(
                BASE + "/" + workflowId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertErrorBody(resp.getBody(), 409, "WORKFLOW_NOT_EDITABLE");
    }

    // ── Error body structure contract ─────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void errorResponse_ContainsRequiredFields() {
        UUID unknown = UUID.randomUUID();
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "/" + unknown, Map.class);

        Map<String, Object> body = resp.getBody();
        // All 5 fields required by GlobalExceptionHandler#buildErrorResponse
        assertThat(body).containsKeys("timestamp", "status", "error", "code", "message");
    }
}
