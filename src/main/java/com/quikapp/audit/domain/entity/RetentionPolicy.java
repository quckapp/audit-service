package com.quikapp.audit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retention_policies", indexes = {
    @Index(name = "idx_retention_workspace", columnList = "workspaceId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private int retentionDays;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuditLog.AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuditLog.AuditSeverity minSeverity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean archiveBeforeDelete;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
