package com.doe.manager.api.controller;

import com.doe.core.model.JobStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies backward compatibility of the legacy {@code /api/v1/jobs} endpoints
 * after all Phase 3 changes.
 *
 * <p>The legacy API must still accept {@code POST /api/v1/jobs},
 * list jobs via {@code GET /api/v1/jobs}, and fetch single jobs
 * via {@code GET /api/v1/jobs/{id}}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyJobApiCompatibilityTest {

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

    private static final String JOBS_BASE = "/api/v1/jobs";

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ── POST /api/v1/jobs ────────────────────────────────────────────────────

    @Test
    void submitJob_Returns201WithPendingStatus() {
        String body = "{\"payload\": \"{\\\"cmd\\\":\\\"echo hello\\\"}\"}";

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                JOBS_BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        Map<String, Object> job = resp.getBody();
        assertThat(job.get("id")).isNotNull();
        assertThat(job.get("status")).isEqualTo(JobStatus.PENDING.name());
        assertThat(job.get("payload")).isEqualTo("{\"cmd\":\"echo hello\"}");
    }

    @Test
    void submitJob_WithBlankPayload_Returns400() {
        String body = "{\"payload\": \"\"}";

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                JOBS_BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── GET /api/v1/jobs ─────────────────────────────────────────────────────

    @Test
    void listJobs_Returns200WithPaginatedContent() {
        // Submit at least one job
        String body = "{\"payload\": \"{\\\"cmd\\\": \\\"list-compat-test\\\"}\"}";
        rest.exchange(JOBS_BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(JOBS_BASE + "?page=0&size=50", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> page = resp.getBody();
        assertThat(page.get("content")).isNotNull();
        assertThat(page.get("totalElements")).isNotNull();
    }

    @Test
    void listJobs_WithStatusFilter_ReturnsPendingOnly() {
        // Submit a job (will be PENDING)
        String body = "{\"payload\": \"{\\\"cmd\\\": \\\"status-filter-compat\\\"}\"}";
        rest.exchange(JOBS_BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(
                JOBS_BASE + "?status=PENDING&page=0&size=50", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> page = resp.getBody();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> content =
                (java.util.List<Map<String, Object>>) page.get("content");
        assertThat(content).allMatch(j -> "PENDING".equals(j.get("status")));
    }

    // ── GET /api/v1/jobs/{id} ─────────────────────────────────────────────────

    @Test
    void getJobById_Returns200WithJobDetail() {
        // Submit a job first
        String body = "{\"payload\": \"{\\\"cmd\\\": \\\"get-by-id-compat\\\"}\"}";
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> created = rest.exchange(
                JOBS_BASE, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                Map.class);
        String jobId = (String) created.getBody().get("id");

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(JOBS_BASE + "/" + jobId, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> job = resp.getBody();
        assertThat(job.get("id")).isEqualTo(jobId);
        assertThat(job.get("payload")).isEqualTo("{\"cmd\": \"get-by-id-compat\"}");
        assertThat(job.get("status")).isEqualTo("PENDING");
    }

    @Test
    void getJobById_UnknownId_Returns404() {
        String unknownId = "00000000-0000-0000-0000-000000000000";
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(JOBS_BASE + "/" + unknownId, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
