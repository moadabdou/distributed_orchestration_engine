package com.doe.manager.workflow;

import com.doe.core.model.Job;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowPersistenceListenerTest {

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
    private WorkflowPersistenceListener listener;

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManager();
        listener = new WorkflowPersistenceListener(
                workflowManager,
                workflowRepository,
                jobRepository,
                jobDependencyRepository
        );
        listener.init(); // registers listener
        
        jobDependencyRepository.deleteAll();
        jobRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    void shouldPersistWorkflowAndDependenciesOnRegister() {
        // Arrange
        Job job1 = Job.newJob("{}").timeoutMs(60000L).build();
        Job job2 = Job.newJob("{}").timeoutMs(60000L).build();

        WorkflowJob wj1 = WorkflowJob.fromJob(job1).dagIndex(0).build();
        WorkflowJob wj2 = WorkflowJob.fromJob(job2).dagIndex(1).dependencies(List.of(job1.getId())).build();

        Workflow wf = Workflow.newWorkflow("test-wf").addJob(wj1).addJob(wj2).build();

        // Act
        workflowManager.registerWorkflow(wf);

        // Assert DB
        WorkflowEntity we = workflowRepository.findById(wf.getId()).orElse(null);
        assertThat(we).isNotNull();
        assertThat(we.getStatus()).isEqualTo(WorkflowStatus.DRAFT);

        List<JobEntity> jobs = jobRepository.findByWorkflowId(wf.getId());
        assertThat(jobs).hasSize(2);

        List<JobDependencyEntity> dependencies = jobDependencyRepository.findByDependentJobId(job2.getId());
        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.get(0).getDependsOn().getId()).isEqualTo(job1.getId());
    }

    @Test
    void shouldUpdateWorkflowStatusOnExecution() {
        // Arrange
        Workflow wf = Workflow.newWorkflow("test-wf")
                .addJob(WorkflowJob.fromJob(Job.newJob("{}").timeoutMs(60000L).build()).dagIndex(0).build())
                .build();
        workflowManager.registerWorkflow(wf);
        
        // Act
        workflowManager.executeWorkflow(wf.getId());

        // Assert
        WorkflowEntity we = workflowRepository.findById(wf.getId()).orElse(null);
        assertThat(we.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    }
}
