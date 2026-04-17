package com.doe.manager.persistence;

import com.doe.core.model.WorkflowStatus;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
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
    private WorkflowRepository workflowRepository;

    @Test
    void shouldSaveAndRetrieveWorkflow() {
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowEntity entity = new WorkflowEntity(workflowId, "test-workflow", WorkflowStatus.DRAFT, now, now);

        workflowRepository.saveAndFlush(entity);

        WorkflowEntity retrieved = workflowRepository.findById(workflowId).orElseThrow();
        assertThat(retrieved.getName()).isEqualTo("test-workflow");
        assertThat(retrieved.getStatus()).isEqualTo(WorkflowStatus.DRAFT);
    }

    @Test
    void shouldFindByStatus() {
        workflowRepository.deleteAll();

        UUID w1 = UUID.randomUUID();
        UUID w2 = UUID.randomUUID();
        Instant now = Instant.now();

        workflowRepository.save(new WorkflowEntity(w1, "draft-1", WorkflowStatus.DRAFT, now, now));
        workflowRepository.save(new WorkflowEntity(w2, "running-1", WorkflowStatus.RUNNING, now, now));
        workflowRepository.flush();

        List<WorkflowEntity> drafts = workflowRepository.findByStatus(WorkflowStatus.DRAFT);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getId()).isEqualTo(w1);
    }
}
