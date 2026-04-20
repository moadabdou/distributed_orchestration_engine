package com.doe.manager.workflow;

import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.persistence.entity.JobDependencyEntity;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.JobDependencyRepository;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
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
class WorkflowRecoveryServiceTest {

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

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobDependencyRepository jobDependencyRepository;

    private WorkflowManager workflowManager;
    private WorkflowRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManager();
        recoveryService = new WorkflowRecoveryService(
                workflowManager,
                workflowRepository,
                jobRepository,
                jobDependencyRepository,
                "PAUSED_ON_RESTART"
        );
        
        jobDependencyRepository.deleteAll();
        jobRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    void shouldRecoverWorkflowsAndTranslateRunningToPaused() {
        // Arrange DB State
        UUID wf1Id = UUID.randomUUID();
        UUID job1Id = UUID.randomUUID();
        UUID job2Id = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowEntity we1 = new WorkflowEntity(wf1Id, "wf-1", WorkflowStatus.RUNNING, now, now);
        workflowRepository.save(we1);

        JobEntity je1 = new JobEntity(job1Id, JobStatus.COMPLETED, "{\"task\":\"t1\"}", 60000L, now, now);
        je1.setWorkflow(we1);
        je1.setDagIndex(0);
        jobRepository.save(je1);

        JobEntity je2 = new JobEntity(job2Id, JobStatus.PENDING, "{\"task\":\"t2\"}", 60000L, now, now);
        je2.setWorkflow(we1);
        je2.setDagIndex(1);
        jobRepository.save(je2);

        JobDependencyEntity dep = new JobDependencyEntity(je2, je1);
        jobDependencyRepository.save(dep);

        // Act
        recoveryService.recoverWorkflows();

        // Assert
        List<Workflow> workflows = workflowManager.listWorkflows();
        assertThat(workflows).hasSize(1);
        
        Workflow recovered = workflows.get(0);
        assertThat(recovered.getId()).isEqualTo(wf1Id);
        // Should be paused due to PAUSED_ON_RESTART logic
        assertThat(recovered.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
        assertThat(recovered.getJobs()).hasSize(2);
        
        assertThat(recovered.getJob(job1Id).getDependencies()).isEmpty();
        assertThat(recovered.getJob(job2Id).getDependencies()).containsExactly(job1Id);
    }
}
