package com.quckapp.audit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_reports", indexes = {
    @Index(name = "idx_report_workspace", columnList = "workspaceId"),
    @Index(name = "idx_report_type", columnList = "reportType"),
    @Index(name = "idx_report_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(columnDefinition = "JSON")
    private String parameters;

    @Column(columnDefinition = "JSON")
    private String summary;

    @Column(length = 500)
    private String fileUrl;

    private Long fileSize;

    @Column(length = 255)
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    public enum ReportType {
        ACCESS_LOG, LOGIN_HISTORY, DATA_EXPORT, SECURITY_AUDIT,
        USER_ACTIVITY, ADMIN_ACTIONS, COMPLIANCE_SUMMARY
    }

    public enum ReportStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
