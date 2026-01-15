package com.quckapp.audit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_workspace", columnList = "workspaceId"),
    @Index(name = "idx_audit_actor", columnList = "actorId"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_resource", columnList = "resourceType, resourceId"),
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    private AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditCategory category;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum AuditSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AuditCategory {
        AUTHENTICATION, AUTHORIZATION, DATA_ACCESS, DATA_MODIFICATION,
        CONFIGURATION, SECURITY, COMPLIANCE, SYSTEM
    }
}
