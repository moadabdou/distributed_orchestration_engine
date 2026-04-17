package com.doe.manager.persistence;

import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.persistence.entity.JobDependencyEntity;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.JobDependencyRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobDependencyRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
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
    private JobDependencyRepository jobDependencyRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobDependencyRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM jobs");
        workflowRepository.deleteAll();
    }

    @Test
    void shouldCascadeDeletesForWorkflow() {
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowEntity workflow = new WorkflowEntity(workflowId, "dep-workflow", WorkflowStatus.DRAFT, now, now);
        workflow = workflowRepository.save(workflow);

        JobEntity job1 = new JobEntity(UUID.randomUUID(), JobStatus.PENDING, "{}", now, now);
        job1.setWorkflow(workflow);
        JobEntity job2 = new JobEntity(UUID.randomUUID(), JobStatus.PENDING, "{}", now, now);
        job2.setWorkflow(workflow);

        entityManager.persist(job1);
        entityManager.persist(job2);

        JobDependencyEntity dep = new JobDependencyEntity(job2, job1);
        jobDependencyRepository.save(dep);

        entityManager.flush();
        entityManager.clear();

        // When we delete the workflow
        workflowRepository.deleteById(workflowId);
        entityManager.flush();

        // Then jobs and dependencies should be cascade deleted
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM jobs", Long.class);
        assertThat(count).isZero();

        List<JobDependencyEntity> deps = jobDependencyRepository.findAll();
        assertThat(deps).isEmpty();
    }

    @Test
    void shouldFindAndDeleteByDependentJobId() {
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowEntity workflow = new WorkflowEntity(workflowId, "dep-workflow-2", WorkflowStatus.DRAFT, now, now);
        workflow = workflowRepository.save(workflow);

        JobEntity job1 = new JobEntity(UUID.randomUUID(), JobStatus.PENDING, "{}", now, now);
        job1.setWorkflow(workflow);
        JobEntity job2 = new JobEntity(UUID.randomUUID(), JobStatus.PENDING, "{}", now, now);
        job2.setWorkflow(workflow);
        JobEntity job3 = new JobEntity(UUID.randomUUID(), JobStatus.PENDING, "{}", now, now);
        job3.setWorkflow(workflow);

        entityManager.persist(job1);
        entityManager.persist(job2);
        entityManager.persist(job3);

        jobDependencyRepository.save(new JobDependencyEntity(job3, job1));
        jobDependencyRepository.save(new JobDependencyEntity(job3, job2));

        entityManager.flush();
        entityManager.clear();

        List<JobDependencyEntity> list = jobDependencyRepository.findByDependentJobId(job3.getId());
        assertThat(list).hasSize(2);

        jobDependencyRepository.deleteByDependentJobId(job3.getId());
        entityManager.flush();

        assertThat(jobDependencyRepository.findAll()).isEmpty();
    }
}
