package com.doe.core.model;

/**
 * Lifecycle states for a {@link Workflow} instance.
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>{@code DRAFT} → {@code RUNNING}</li>
 *   <li>{@code RUNNING} → {@code PAUSED}, {@code COMPLETED}, {@code FAILED}</li>
 *   <li>{@code PAUSED} → {@code RUNNING}, {@code FAILED}</li>
 *   <li>{@code COMPLETED} and {@code FAILED} are terminal states.</li>
 * </ul>
 */
public enum WorkflowStatus {

    DRAFT {
        @Override
        public boolean canTransitionTo(WorkflowStatus target) {
            return target == RUNNING;
        }
    },

    RUNNING {
        @Override
        public boolean canTransitionTo(WorkflowStatus target) {
            return target == PAUSED || target == COMPLETED || target == FAILED;
        }
    },

    PAUSED {
        @Override
        public boolean canTransitionTo(WorkflowStatus target) {
            return target == RUNNING || target == FAILED;
        }
    },

    COMPLETED {
        @Override
        public boolean canTransitionTo(WorkflowStatus target) {
            return false;
        }
    },

    FAILED {
        @Override
        public boolean canTransitionTo(WorkflowStatus target) {
            return false;
        }
    };

    /**
     * Returns {@code true} if this status may legally transition to {@code target}.
     */
    public abstract boolean canTransitionTo(WorkflowStatus target);
}
