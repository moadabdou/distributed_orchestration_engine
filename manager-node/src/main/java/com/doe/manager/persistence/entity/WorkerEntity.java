package com.doe.manager.persistence.entity;

import com.doe.core.model.WorkerStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a worker node stored in the {@code workers} table.
 *
 * <p>Timestamps ({@code registeredAt}, {@code lastHeartbeat}) are set explicitly
 * from the in-memory state — the DB acts as an event log and owns no timestamp
 * generation logic.
 */
@Entity
@Table(name = "workers")
public class WorkerEntity {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerStatus status;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    /** Required by JPA. */
    protected WorkerEntity() {}

    public WorkerEntity(UUID id, String hostname, String ipAddress,
                        WorkerStatus status, Instant lastHeartbeat, Instant registeredAt) {
        this.id = id;
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
        this.registeredAt = registeredAt;
    }

    // ─── Getters & setters ──────────────────────────────────────────────────

    public UUID getId()                  { return id; }
    public String getHostname()          { return hostname; }
    public String getIpAddress()         { return ipAddress; }
    public WorkerStatus getStatus()      { return status; }
    public Instant getLastHeartbeat()    { return lastHeartbeat; }
    public Instant getRegisteredAt()     { return registeredAt; }

    public void setStatus(WorkerStatus status)          { this.status = status; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public void setHostname(String hostname)            { this.hostname = hostname; }
    public void setIpAddress(String ipAddress)          { this.ipAddress = ipAddress; }
}
