package com.doe.manager.persistence.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class JobDependencyId implements Serializable {
    private UUID dependentJobId;
    private UUID dependsOnId;

    public JobDependencyId() {}

    public JobDependencyId(UUID dependentJobId, UUID dependsOnId) {
        this.dependentJobId = dependentJobId;
        this.dependsOnId = dependsOnId;
    }

    public UUID getDependentJobId() { return dependentJobId; }
    public void setDependentJobId(UUID dependentJobId) { this.dependentJobId = dependentJobId; }

    public UUID getDependsOnId() { return dependsOnId; }
    public void setDependsOnId(UUID dependsOnId) { this.dependsOnId = dependsOnId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobDependencyId that = (JobDependencyId) o;
        return Objects.equals(dependentJobId, that.dependentJobId) &&
                Objects.equals(dependsOnId, that.dependsOnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependentJobId, dependsOnId);
    }
}
