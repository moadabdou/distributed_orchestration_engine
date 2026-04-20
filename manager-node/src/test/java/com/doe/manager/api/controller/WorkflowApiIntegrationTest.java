package com.doe.manager.api.controller;

import org.springframework.test.context.ActiveProfiles;

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
 * Full-stack integration tests for the Workflow REST API.
 *
 * <p>Uses {@code @ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT)} so all layers —
 * controller, service, WorkflowManager, persistence, Flyway — are active.
 * A real PostgreSQL container is started via Testcontainers.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowApiIntegrationTest {

    @Container
    @SuppressWarnings("resource")
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final String BASE = "/api/v1/workflows";

    /** POST body for a simple linear workflow: A → B */
    private String linearWorkflowJson(String name) {
        return """
                {
                  "name": "%s",
                  "jobs": [
                    {"label":"A","payload":"{\\"task\\":\\"A\\"}"},
                    {"label":"B","payload":"{\\"task\\":\\"B\\"}"}
                  ],
                  "dependencies": [{"fromJobLabel":"A","toJobLabel":"B"}]
                }
                """.formatted(name);
    }

    /** POST body for a diamond DAG workflow: A → B, A → C, B+C → D */
    private String diamondWorkflowJson(String name) {
        return """
                {
                  "name": "%s",
                  "jobs": [
                    {"label":"A","payload":"{\\"task\\":\\"A\\"}"},
                    {"label":"B","payload":"{\\"task\\":\\"B\\"}"},
                    {"label":"C","payload":"{\\"task\\":\\"C\\"}"},
                    {"label":"D","payload":"{\\"task\\":\\"D\\"}"}
                  ],
                  "dependencies": [
                    {"fromJobLabel":"A","toJobLabel":"B"},
                    {"fromJobLabel":"A","toJobLabel":"C"},
                    {"fromJobLabel":"B","toJobLabel":"D"},
                    {"fromJobLabel":"C","toJobLabel":"D"}
                  ]
                }
                """.formatted(name);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createWorkflow(String body) {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void createWorkflow_ReturnsCreatedWithCorrectFields() {
        Map<String, Object> body = createWorkflow(linearWorkflowJson("linear-test"));

        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("name")).isEqualTo("linear-test");
        assertThat(body.get("status")).isEqualTo("DRAFT");
        assertThat(body.get("totalJobs")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createDiamondDag_ReturnsCorrectNodeAndEdgeCount() {
        Map<String, Object> created = createWorkflow(diamondWorkflowJson("diamond-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> dagResp = rest.getForEntity(
                BASE + "/" + workflowId + "/dag", Map.class);
        assertThat(dagResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> dag = dagResp.getBody();
        assertThat(dag.get("workflowId")).isEqualTo(workflowId.toString());
        assertThat((java.util.List<?>) dag.get("nodes")).hasSize(4);
        assertThat((java.util.List<?>) dag.get("edges")).hasSize(4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listWorkflows_ReturnsPaginatedResults() {
        // Create two workflows so there's something in the list
        createWorkflow(linearWorkflowJson("list-test-1"));
        createWorkflow(linearWorkflowJson("list-test-2"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "?page=0&size=50", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> page = resp.getBody();
        assertThat((java.util.List<?>) page.get("content")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(page.get("totalElements")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listWorkflows_WithStatusFilter_ReturnsOnlyMatchingWorkflows() {
        createWorkflow(linearWorkflowJson("status-filter-test"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "?status=DRAFT", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> page = resp.getBody();
        java.util.List<Map<String, Object>> content =
                (java.util.List<Map<String, Object>>) page.get("content");
        // All returned items must have status DRAFT
        assertThat(content).allMatch(w -> "DRAFT".equals(w.get("status")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getWorkflow_ReturnsSameWorkflowAsCreated() {
        Map<String, Object> created = createWorkflow(linearWorkflowJson("get-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(BASE + "/" + workflowId, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = resp.getBody();
        assertThat(body.get("id")).isEqualTo(workflowId.toString());
        assertThat(body.get("name")).isEqualTo("get-test");
        assertThat(body.get("totalJobs")).isEqualTo(2);
    }

    @Test
    void updateWorkflow_ReplacesJobCount() {
        Map<String, Object> created = createWorkflow(linearWorkflowJson("update-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        // Replace with a single-job workflow
        String updateBody = """
                {
                  "name": "updated-workflow",
                  "jobs": [{"label":"X","payload":"{\\"task\\":\\"X\\"}"}],
                  "dependencies": []
                }
                """;

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                BASE + "/" + workflowId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, jsonHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("totalJobs")).isEqualTo(1);
        assertThat(resp.getBody().get("name")).isEqualTo("updated-workflow");
    }

    @Test
    void deleteWorkflow_Returns204AndSubsequentGetReturns404() {
        Map<String, Object> created = createWorkflow(linearWorkflowJson("delete-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        ResponseEntity<Void> del = rest.exchange(
                BASE + "/" + workflowId, HttpMethod.DELETE,
                HttpEntity.EMPTY, Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> get = rest.getForEntity(BASE + "/" + workflowId, Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void executeWorkflow_ChangesStatusToRunning() {
        Map<String, Object> created = createWorkflow(linearWorkflowJson("execute-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                BASE + "/" + workflowId + "/execute", HttpMethod.POST,
                HttpEntity.EMPTY, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("RUNNING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDag_AfterCreate_ReturnsNodesAndEdgesForLinearWorkflow() {
        Map<String, Object> created = createWorkflow(linearWorkflowJson("dag-test"));
        UUID workflowId = UUID.fromString((String) created.get("id"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(
                BASE + "/" + workflowId + "/dag", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dag = resp.getBody();
        assertThat(dag.get("workflowId")).isEqualTo(workflowId.toString());

        java.util.List<?> nodes = (java.util.List<?>) dag.get("nodes");
        java.util.List<?> edges = (java.util.List<?>) dag.get("edges");

        assertThat(nodes).hasSize(2);   // A and B
        assertThat(edges).hasSize(1);   // A → B
    }
}
