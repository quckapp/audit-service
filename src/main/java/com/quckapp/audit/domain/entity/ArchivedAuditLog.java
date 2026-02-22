package com.quckapp.audit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "archived_audit_logs", indexes = {
    @Index(name = "idx_archived_workspace", columnList = "workspaceId"),
    @Index(name = "idx_archived_actor", columnList = "actorId"),
    @Index(name = "idx_archived_action", columnList = "action"),
    @Index(name = "idx_archived_resource", columnList = "resourceType, resourceId"),
    @Index(name = "idx_archived_created", columnList = "createdAt"),
    @Index(name = "idx_archived_policy", columnList = "archivedByPolicyId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ArchivedAuditLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID actorId;

    @Column(length = 100)
    private String actorEmail;

    @Column(length = 100)
    private String actorName;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 50)
    private String resourceType;

    @Column(nullable = false)
    private UUID resourceId;

    @Column(length = 255)
    private String resourceName;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(columnDefinition = "JSON")
    private String previousState;

    @Column(columnDefinition = "JSON")
    private String newState;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Column(length = 50)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditLog.AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditLog.AuditCategory category;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant archivedAt;

    @Column(nullable = false)
    private UUID archivedByPolicyId;

    public static ArchivedAuditLog fromAuditLog(AuditLog auditLog, UUID policyId) {
        return ArchivedAuditLog.builder()
            .id(auditLog.getId())
            .workspaceId(auditLog.getWorkspaceId())
            .actorId(auditLog.getActorId())
            .actorEmail(auditLog.getActorEmail())
            .actorName(auditLog.getActorName())
            .action(auditLog.getAction())
            .resourceType(auditLog.getResourceType())
            .resourceId(auditLog.getResourceId())
            .resourceName(auditLog.getResourceName())
            .metadata(auditLog.getMetadata())
            .previousState(auditLog.getPreviousState())
            .newState(auditLog.getNewState())
            .ipAddress(auditLog.getIpAddress())
            .userAgent(auditLog.getUserAgent())
            .sessionId(auditLog.getSessionId())
            .severity(auditLog.getSeverity())
            .category(auditLog.getCategory())
            .createdAt(auditLog.getCreatedAt())
            .archivedAt(Instant.now())
            .archivedByPolicyId(policyId)
            .build();
    }
}
