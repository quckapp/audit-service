package com.quckapp.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AuditDtos {

    // ===== Audit Log DTOs =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateAuditLogRequest {
        @NotNull private UUID workspaceId;
        @NotNull private UUID actorId;
        private String actorEmail;
        private String actorName;
        @NotBlank @Size(max = 100) private String action;
        @NotBlank @Size(max = 50) private String resourceType;
        @NotNull private UUID resourceId;
        private String resourceName;
        private Map<String, Object> metadata;
        private String previousState;
        private String newState;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        @NotNull private AuditLog.AuditSeverity severity;
        @NotNull private AuditLog.AuditCategory category;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AuditLogResponse {
        private UUID id;
        private UUID workspaceId;
        private UUID actorId;
        private String actorEmail;
        private String actorName;
        private String action;
        private String resourceType;
        private UUID resourceId;
        private String resourceName;
        private Map<String, Object> metadata;
        private String previousState;
        private String newState;
        private String ipAddress;
        private String userAgent;
        private AuditLog.AuditSeverity severity;
        private AuditLog.AuditCategory category;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuditLogSearchRequest {
        private UUID workspaceId;
        private UUID actorId;
        private String action;
        private String resourceType;
        private UUID resourceId;
        private AuditLog.AuditCategory category;
        private Set<AuditLog.AuditSeverity> severities;
        private Instant startDate;
        private Instant endDate;
        private String query;
        @Min(0) private int page;
        @Min(1) @Max(100) private int size = 20;
    }

    // ===== Retention Policy DTOs =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRetentionPolicyRequest {
        @NotNull private UUID workspaceId;
        @NotBlank @Size(max = 100) private String name;
        @Size(max = 255) private String description;
        @Min(1) @Max(3650) private int retentionDays;
        private AuditLog.AuditCategory category;
        private AuditLog.AuditSeverity minSeverity;
        private boolean archiveBeforeDelete;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRetentionPolicyRequest {
        @Size(max = 100) private String name;
        @Size(max = 255) private String description;
        @Min(1) @Max(3650) private Integer retentionDays;
        private AuditLog.AuditSeverity minSeverity;
        private Boolean enabled;
        private Boolean archiveBeforeDelete;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RetentionPolicyResponse {
        private UUID id;
        private UUID workspaceId;
        private String name;
        private String description;
        private int retentionDays;
        private AuditLog.AuditCategory category;
        private AuditLog.AuditSeverity minSeverity;
        private boolean enabled;
        private boolean archiveBeforeDelete;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ===== Compliance Report DTOs =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateReportRequest {
        @NotNull private UUID workspaceId;
        @NotBlank @Size(max = 100) private String name;
        @NotNull private ComplianceReport.ReportType reportType;
        @NotNull private Instant periodStart;
        @NotNull private Instant periodEnd;
        private Map<String, Object> parameters;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplianceReportResponse {
        private UUID id;
        private UUID workspaceId;
        private String name;
        private ComplianceReport.ReportType reportType;
        private ComplianceReport.ReportStatus status;
        private Instant periodStart;
        private Instant periodEnd;
        private UUID requestedBy;
        private Map<String, Object> parameters;
        private Map<String, Object> summary;
        private String fileUrl;
        private Long fileSize;
        private String errorMessage;
        private Instant createdAt;
        private Instant completedAt;
    }

    // ===== Statistics DTOs =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuditStatistics {
        private UUID workspaceId;
        private Instant periodStart;
        private Instant periodEnd;
        private long totalEvents;
        private Map<String, Long> eventsByAction;
        private Map<String, Long> eventsByCategory;
        private Map<String, Long> eventsBySeverity;
        private List<TopActor> topActors;
        private List<TopResource> topResources;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopActor {
        private UUID actorId;
        private String actorEmail;
        private String actorName;
        private long eventCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopResource {
        private String resourceType;
        private UUID resourceId;
        private String resourceName;
        private long eventCount;
    }

    // ===== API Response =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private Instant timestamp;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder().success(true).data(data).timestamp(Instant.now()).build();
        }
        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder().success(true).message(message).data(data).timestamp(Instant.now()).build();
        }
        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder().success(false).message(message).timestamp(Instant.now()).build();
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PagedResponse<T> {
        @JsonProperty("items")
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }
}
