package com.doe.core.util;

import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless utility that validates the structural integrity of a workflow DAG.
 *
 * <p>Validation checks:
 * <ol>
 *   <li><b>Self-dependency</b> — a job must not list itself as a dependency.</li>
 *   <li><b>Missing dependency</b> — every dependency must reference a job that
 *       exists in the workflow.</li>
 *   <li><b>Cycle detection</b> — the graph must be a true DAG (no cycles),
 *       verified via DFS with coloring.</li>
 * </ol>
 *
 * <p>This class is thread-safe and stateless — it holds no mutable state.
 */
public final class DagValidator {

    private DagValidator() {
        // utility class
    }

    /**
     * Validates the given workflow and returns a list of all validation errors.
     *
     * @return an empty list if the DAG is valid, or a list of
     *         {@link DagValidationError} describing every problem found.
     */
    public static List<DagValidationError> validate(Workflow workflow) {
        List<DagValidationError> errors = new ArrayList<>();
        errors.addAll(checkSelfDependencies(workflow));
        errors.addAll(checkMissingDependencies(workflow));
        errors.addAll(checkCycles(workflow));
        return errors;
    }

    /**
     * Returns {@code true} if the workflow DAG passes all validation checks.
     */
    public static boolean isValid(Workflow workflow) {
        return validate(workflow).isEmpty();
    }

    // ──── Self-dependency check ─────────────────────────────────────────────

    private static List<DagValidationError> checkSelfDependencies(Workflow workflow) {
        List<DagValidationError> errors = new ArrayList<>();
        for (WorkflowJob wj : workflow.getJobs()) {
            if (wj.getDependencies().contains(wj.getJob().getId())) {
                errors.add(DagValidationError.selfDependency(wj.getJob().getId()));
            }
        }
        return errors;
    }

    // ──── Missing dependency check ───────────────────────────────────────────

    private static List<DagValidationError> checkMissingDependencies(Workflow workflow) {
        Set<UUID> knownIds = workflow.getJobs().stream()
                .map(wj -> wj.getJob().getId())
                .collect(Collectors.toSet());

        List<DagValidationError> errors = new ArrayList<>();
        for (WorkflowJob wj : workflow.getJobs()) {
            for (UUID depId : wj.getDependencies()) {
                if (!knownIds.contains(depId)) {
                    errors.add(DagValidationError.missingDependency(wj.getJob().getId(), depId));
                }
            }
        }
        return errors;
    }

    // ──── Cycle detection (DFS with 3-color marking) ─────────────────────────

    private enum Color { WHITE, GRAY, BLACK }

    private static List<DagValidationError> checkCycles(Workflow workflow) {
        List<DagValidationError> errors = new ArrayList<>();
        List<WorkflowJob> jobs = workflow.getJobs();
        Set<UUID> knownIds = jobs.stream()
                .map(wj -> wj.getJob().getId())
                .collect(Collectors.toSet());

        // Build adjacency list (only include edges to known job IDs)
        Map<UUID, List<UUID>> adj = new LinkedHashMap<>();
        Map<UUID, Color> color = new LinkedHashMap<>();
        for (WorkflowJob wj : jobs) {

            List<UUID> validDeps = wj.getDependencies().stream()
                    .filter(knownIds::contains)
                    .toList();
            adj.put(wj.getJob().getId(), List.copyOf(validDeps));

            color.put(wj.getJob().getId(), Color.WHITE);
        }

        Set<String> reportedCycles = new HashSet<>();

        for (WorkflowJob wj : jobs) {
            if (color.get(wj.getJob().getId()) == Color.WHITE) {
                List<UUID> path = new ArrayList<>();
                List<UUID> cycle = dfsDetectCycle(wj.getJob().getId(), adj, color, path, reportedCycles);
                if (cycle != null) {
                    errors.add(DagValidationError.cycle(cycle));
                }
            }
        }
        return errors;
    }

    /**
     * Recursive DFS that returns a cycle list if a back-edge to a GRAY node is found.
     *
     * @param node    current node being visited
     * @param adj     adjacency list of the DAG
     * @param color   visitation state per node
     * @param path    current DFS recursion stack (for cycle reconstruction)
     * @param reported  set of already-reported cycle signatures
     * @return the cycle path if one is found, or null if this subtree is acyclic
     */
    private static List<UUID> dfsDetectCycle(
            UUID node,
            Map<UUID, List<UUID>> adj,
            Map<UUID, Color> color,
            List<UUID> path,
            Set<String> reported
    ) {
        color.put(node, Color.GRAY);
        path.add(node);

        for (UUID neighbor : adj.getOrDefault(node, List.of())) {
            Color neighborColor = color.get(neighbor);
            if (neighborColor == Color.GRAY) {
                // Back edge — reconstruct cycle from path
                int startIdx = path.indexOf(neighbor);
                if (startIdx >= 0) {
                    List<UUID> cycle = new ArrayList<>(path.subList(startIdx, path.size()));
                    cycle.add(neighbor); // close the loop
                    String signature = cycle.stream()
                            .map(UUID::toString)
                            .sorted()
                            .collect(Collectors.joining(","));
                    if (!reported.contains(signature)) {
                        reported.add(signature);
                        return List.copyOf(cycle);
                    }
                }
            }
            if (neighborColor == Color.WHITE) {
                List<UUID> result = dfsDetectCycle(neighbor, adj, color, path, reported);
                if (result != null) {
                    return result;
                }
            }
        }

        path.remove(path.size() - 1);
        color.put(node, Color.BLACK);
        return null;
    }
}
