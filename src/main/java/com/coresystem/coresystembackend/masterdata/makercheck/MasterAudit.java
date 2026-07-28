package com.coresystem.coresystembackend.masterdata.makercheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit before/after snapshot per Tier A mutation (BR-BE07-04, DB-CONVENTIONS §4 log_).
 * Append-only INSERT-only — no updated_at/updated_by (log_ table convention).
 */
@Entity
@Table(name = "log_master_audit")
public class MasterAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "actor_nik", nullable = false)
    private String actorNik;

    @Column(name = "action_performed_at", nullable = false)
    private Instant actionPerformedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    public MasterAudit() {}

    public MasterAudit(String entityType, String entityId, String beforeJson, String afterJson, String actorNik) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.actorNik = actorNik;
        this.actionPerformedAt = Instant.now();
        this.createdAt = this.actionPerformedAt;
        this.createdBy = actorNik;
    }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public String getActorNik() { return actorNik; }
    public Instant getActionPerformedAt() { return actionPerformedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
// SDD-PROVENANCE: drift-fix F-001 | vault: .mega-sdd/vaults/acquisition-master-data | MasterAudit log_master_audit append-only (BR-BE07-04)
