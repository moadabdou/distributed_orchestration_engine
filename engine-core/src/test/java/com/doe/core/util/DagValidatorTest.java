package com.doe.core.util;

import com.doe.core.model.Job;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.util.DagValidationError.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DagValidatorTest {

    // ──── Helper ─────────────────────────────────────────────────────────────

    /** Creates a WorkflowJob wrapping a fresh Job, with the given deps and index. */
    private static WorkflowJob makeJob(int index, List<UUID> dependencies) {
        return WorkflowJob.fromJob(Job.newJob("{\"task\":\"node" + index + "\"}").build())
                .dagIndex(index)
                .dependencies(dependencies)
                .build();
    }

    /** Returns the ID of the wrapped job. */
    private static UUID id(WorkflowJob wj) {
        return wj.getJob().getId();
    }

    /** Builds a workflow from the given jobs (auto-indexed). */
    private static Workflow buildWorkflow(String name, WorkflowJob... jobs) {
        Workflow.Builder builder = Workflow.newWorkflow(name);
        for (WorkflowJob wj : jobs) {
            builder.addJob(wj);
        }
        return builder.build();
    }

    // ──── Valid DAG scenarios ────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid DAGs")
    class ValidDags {

        @Test
        @DisplayName("single node with no dependencies")
        void singleNode() {
            WorkflowJob a = makeJob(0, List.of());
            assertTrue(DagValidator.isValid(buildWorkflow("single", a)));
            assertTrue(DagValidator.validate(buildWorkflow("single", a)).isEmpty());
        }

        @Test
        @DisplayName("linear chain: A → B → C")
        void linearChain() {
            WorkflowJob a = makeJob(0, List.of());
            WorkflowJob b = makeJob(1, List.of(id(a)));
            WorkflowJob c = makeJob(2, List.of(id(b)));
            Workflow wf = buildWorkflow("linear", a, b, c);

            assertTrue(DagValidator.isValid(wf));
        }

        @Test
        @DisplayName("diamond: A → B, A → C, B → D, C → D")
        void diamondGraph() {
            WorkflowJob a = makeJob(0, List.of());
            WorkflowJob b = makeJob(1, List.of(id(a)));
            WorkflowJob c = makeJob(2, List.of(id(a)));
            WorkflowJob d = makeJob(3, List.of(id(b), id(c)));
            Workflow wf = buildWorkflow("diamond", a, b, c, d);

            assertTrue(DagValidator.isValid(wf));
        }

        @Test
        @DisplayName("complex DAG with multiple roots and sinks")
        void complexDag() {
            WorkflowJob a = makeJob(0, List.of());
            WorkflowJob b = makeJob(1, List.of());
            WorkflowJob c = makeJob(2, List.of(id(a)));
            WorkflowJob d = makeJob(3, List.of(id(a), id(b)));
            WorkflowJob e = makeJob(4, List.of(id(c), id(d)));
            Workflow wf = buildWorkflow("complex", a, b, c, d, e);

            assertTrue(DagValidator.isValid(wf));
        }

        @Test
        @DisplayName("empty workflow is valid")
        void emptyWorkflow() {
            Workflow wf = Workflow.newWorkflow("empty").build();
            assertTrue(DagValidator.isValid(wf));
        }
    }

    // ──── Self-dependency ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Self-dependency detection")
    class SelfDependency {

        @Test
        @DisplayName("job depends on itself")
        void directSelfDependency() {
            WorkflowJob a = makeJob(0, List.of());
            // We need the job's own ID
            Job job = Job.newJob("{\"task\":\"self\"}").build();
            WorkflowJob selfDep2 = WorkflowJob.fromJob(job)
                    .dagIndex(1)
                    .dependencies(List.of(job.getId()))
                    .build();
            Workflow wf = buildWorkflow("self-dep", a, selfDep2);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.SELF_DEPENDENCY));
        }

        @Test
        @DisplayName("multiple self-dependencies are all reported")
        void multipleSelfDependencies() {
            Job j1 = Job.newJob("{\"task\":\"a\"}").build();
            Job j2 = Job.newJob("{\"task\":\"b\"}").build();
            WorkflowJob wj1 = WorkflowJob.fromJob(j1).dagIndex(0).dependencies(List.of(j1.getId())).build();
            WorkflowJob wj2 = WorkflowJob.fromJob(j2).dagIndex(1).dependencies(List.of(j2.getId())).build();
            Workflow wf = buildWorkflow("multi-self", wj1, wj2);

            List<DagValidationError> errors = DagValidator.validate(wf);
            // Each self-dependency is caught by both self-dep check and cycle check
            assertTrue(errors.stream().filter(e -> e.type() == ErrorType.SELF_DEPENDENCY).count() >= 2);
        }
    }

    // ──── Missing dependency ────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing dependency detection")
    class MissingDependency {

        @Test
        @DisplayName("dependency on non-existent job")
        void missingDependency() {
            WorkflowJob a = makeJob(0, List.of());
            UUID ghostId = UUID.randomUUID();
            WorkflowJob b = makeJob(1, List.of(ghostId));
            Workflow wf = buildWorkflow("missing", a, b);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.MISSING_DEPENDENCY));
        }

        @Test
        @DisplayName("multiple missing dependencies are all reported")
        void multipleMissingDependencies() {
            WorkflowJob a = makeJob(0, List.of());
            UUID ghost1 = UUID.randomUUID();
            UUID ghost2 = UUID.randomUUID();
            WorkflowJob b = makeJob(1, List.of(ghost1));
            WorkflowJob c = makeJob(2, List.of(ghost2));
            Workflow wf = buildWorkflow("multi-missing", a, b, c);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertEquals(2, errors.size());
            assertTrue(errors.stream().allMatch(e -> e.type() == ErrorType.MISSING_DEPENDENCY));
        }
    }

    // ──── Cycle detection ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Cycle detection")
    class Cycles {

        @Test
        @DisplayName("simple 2-node cycle: A → B → A")
        void twoNodeCycle() {
            Job jA = Job.newJob("{\"task\":\"a\"}").build();
            Job jB = Job.newJob("{\"task\":\"b\"}").build();
            WorkflowJob a = WorkflowJob.fromJob(jA).dagIndex(0).dependencies(List.of(jB.getId())).build();
            WorkflowJob b = WorkflowJob.fromJob(jB).dagIndex(1).dependencies(List.of(jA.getId())).build();
            Workflow wf = buildWorkflow("cycle-2", a, b);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.CYCLE));
        }

        @Test
        @DisplayName("3-node cycle: A → B → C → A")
        void threeNodeCycle() {
            Job jA = Job.newJob("{\"task\":\"a\"}").build();
            Job jB = Job.newJob("{\"task\":\"b\"}").build();
            Job jC = Job.newJob("{\"task\":\"c\"}").build();
            WorkflowJob a = WorkflowJob.fromJob(jA).dagIndex(0).dependencies(List.of(jC.getId())).build();
            WorkflowJob b = WorkflowJob.fromJob(jB).dagIndex(1).dependencies(List.of(jA.getId())).build();
            WorkflowJob c = WorkflowJob.fromJob(jC).dagIndex(2).dependencies(List.of(jB.getId())).build();
            Workflow wf = buildWorkflow("cycle-3", a, b, c);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.CYCLE));
        }

        @Test
        @DisplayName("cycle within a larger DAG")
        void cycleInLargerDag() {
            Job jA = Job.newJob("{\"task\":\"a\"}").build();
            Job jB = Job.newJob("{\"task\":\"b\"}").build();
            Job jC = Job.newJob("{\"task\":\"c\"}").build();
            Job jD = Job.newJob("{\"task\":\"d\"}").build();
            // A depends on D , B depends on A, C depends on B, D depends on B 
            // A -> D -> B -> A (cycle), C -> B -> A -> D -> B (cycle)

            WorkflowJob a = WorkflowJob.fromJob(jA).dagIndex(0).dependencies(List.of(jD.getId())).build();
            WorkflowJob b = WorkflowJob.fromJob(jB).dagIndex(1).dependencies(List.of(jA.getId())).build();
            WorkflowJob c = WorkflowJob.fromJob(jC).dagIndex(2).dependencies(List.of(jB.getId())).build();
            WorkflowJob d = WorkflowJob.fromJob(jD).dagIndex(3).dependencies(List.of(jB.getId())).build();
            Workflow wf = buildWorkflow("cycle-in-dag", a, b, c, d);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.CYCLE));
        }

        @Test
        @DisplayName("diamond with a back-edge creates a cycle")
        void diamondWithBackEdge() {
            Job jA = Job.newJob("{\"task\":\"a\"}").build();
            Job jB = Job.newJob("{\"task\":\"b\"}").build();
            Job jC = Job.newJob("{\"task\":\"c\"}").build();
            Job jD = Job.newJob("{\"task\":\"d\"}").build();
            // A → B, A → C, B → D, C → D, D → A (cycle!)
            WorkflowJob a = WorkflowJob.fromJob(jA).dagIndex(0).dependencies(List.of(jD.getId())).build();
            WorkflowJob b = WorkflowJob.fromJob(jB).dagIndex(1).dependencies(List.of(jA.getId())).build();
            WorkflowJob c = WorkflowJob.fromJob(jC).dagIndex(2).dependencies(List.of(jA.getId())).build();
            WorkflowJob d = WorkflowJob.fromJob(jD).dagIndex(3).dependencies(List.of(jB.getId(), jC.getId())).build();
            Workflow wf = buildWorkflow("diamond-cycle", a, b, c, d);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.CYCLE));
        }
    }

    // ──── Combined validation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Combined validation")
    class CombinedValidation {

        @Test
        @DisplayName("workflow with both self-dep and missing dep reports both")
        void selfDepAndMissingDep() {
            Job j1 = Job.newJob("{\"task\":\"a\"}").build();
            Job j2 = Job.newJob("{\"task\":\"b\"}").build();
            UUID ghost = UUID.randomUUID();
            WorkflowJob wj1 = WorkflowJob.fromJob(j1).dagIndex(0).dependencies(List.of(j1.getId())).build();
            WorkflowJob wj2 = WorkflowJob.fromJob(j2).dagIndex(1).dependencies(List.of(ghost)).build();
            Workflow wf = buildWorkflow("combined", wj1, wj2);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.SELF_DEPENDENCY));
            assertTrue(errors.stream().anyMatch(e -> e.type() == ErrorType.MISSING_DEPENDENCY));
        }

        @Test
        @DisplayName("error messages are descriptive")
        void errorMessagesAreDescriptive() {
            Job j1 = Job.newJob("{\"task\":\"a\"}").build();
            WorkflowJob wj1 = WorkflowJob.fromJob(j1).dagIndex(0).dependencies(List.of(j1.getId())).build();
            Workflow wf = buildWorkflow("msg-test", wj1);

            List<DagValidationError> errors = DagValidator.validate(wf);
            assertFalse(errors.isEmpty());
            String msg = errors.get(0).message();
            assertNotNull(msg);
            assertFalse(msg.isBlank());
            assertTrue(msg.contains("depends on itself"));
        }
    }
}
