package com.doe.manager.persistence.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DataDependencyId implements Serializable {
    private UUID sourceJobId;
    private UUID targetJobId;

    public DataDependencyId() {}

    public DataDependencyId(UUID sourceJobId, UUID targetJobId) {
        this.sourceJobId = sourceJobId;
        this.targetJobId = targetJobId;
    }

    public UUID getSourceJobId() { return sourceJobId; }
    public void setSourceJobId(UUID sourceJobId) { this.sourceJobId = sourceJobId; }

    public UUID getTargetJobId() { return targetJobId; }
    public void setTargetJobId(UUID targetJobId) { this.targetJobId = targetJobId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataDependencyId that = (DataDependencyId) o;
        return Objects.equals(sourceJobId, that.sourceJobId) &&
                Objects.equals(targetJobId, that.targetJobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceJobId, targetJobId);
    }
}
