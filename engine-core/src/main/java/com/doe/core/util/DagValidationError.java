package com.doe.core.util;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable error descriptor produced by {@link DagValidator} when a DAG
 * structure fails validation.
 *
 * @param type    the category of validation failure
 * @param message human-readable description of the problem
 * @param jobIds  the job IDs involved in the validation failure
 */
public record DagValidationError(
        ErrorType type,
        String message,
        List<UUID> jobIds
) {

    /**
     * Enumeration of distinct DAG validation failure modes.
     */
    public enum ErrorType {
        /** A job lists itself as a dependency. */
        SELF_DEPENDENCY,

        /** A dependency references a job ID that does not exist in the workflow. */
        MISSING_DEPENDENCY,

        /** The dependency graph contains one or more cycles. */
        CYCLE
    }

    /**
     * Convenience factory for a self-dependency error.
     */
    public static DagValidationError selfDependency(UUID jobId) {
        return new DagValidationError(
                ErrorType.SELF_DEPENDENCY,
                "Job %s depends on itself".formatted(jobId),
                List.of(jobId)
        );
    }

    /**
     * Convenience factory for a missing dependency error.
     */
    public static DagValidationError missingDependency(UUID jobId, UUID missingDepId) {
        return new DagValidationError(
                ErrorType.MISSING_DEPENDENCY,
                "Job %s depends on job %s which does not exist in the workflow"
                        .formatted(jobId, missingDepId),
                List.of(jobId, missingDepId)
        );
    }

    /**
     * Convenience factory for a cycle error.
     */
    public static DagValidationError cycle(List<UUID> cyclePath) {
        String path = cyclePath.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(" → "));
        return new DagValidationError(
                ErrorType.CYCLE,
                "Cycle detected in DAG: %s".formatted(path),
                cyclePath
        );
    }

    public DagValidationError {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(jobIds, "jobIds");
    }
}
