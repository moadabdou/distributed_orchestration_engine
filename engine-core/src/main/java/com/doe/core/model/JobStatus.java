package com.doe.core.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a {@link Job}.
 *
 * <pre>
 * PENDING ──→ ASSIGNED ──→ RUNNING ──→ COMPLETED
 *                 │            │
 *                 │            └──→ FAILED
 *                 │
 *                 └──→ PENDING  (re-queue on timeout, failed assignment)
 * </pre>
 */
public enum JobStatus {

    PENDING {
        @Override
        public Set<JobStatus> validTransitions() {
            return EnumSet.of(ASSIGNED);
        }
    },

    ASSIGNED {
        @Override
        public Set<JobStatus> validTransitions() {
            return EnumSet.of(RUNNING, PENDING);
        }
    },

    RUNNING {
        @Override
        public Set<JobStatus> validTransitions() {
            return EnumSet.of(COMPLETED, FAILED);
        }
    },

    COMPLETED {
        @Override
        public Set<JobStatus> validTransitions() {
            return EnumSet.noneOf(JobStatus.class);
        }
    },

    FAILED {
        @Override
        public Set<JobStatus> validTransitions() {
            return EnumSet.noneOf(JobStatus.class);
        }
    };

    /**
     * Returns the set of statuses this status can transition to.
     */
    public abstract Set<JobStatus> validTransitions();

    /**
     * Returns {@code true} if a transition to {@code target} is permitted.
     */
    public boolean canTransitionTo(JobStatus target) {
        return validTransitions().contains(target);
    }
}
