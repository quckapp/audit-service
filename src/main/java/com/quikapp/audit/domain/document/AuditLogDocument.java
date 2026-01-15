package com.quckapp.audit.domain.document;

import com.quckapp.audit.domain.entity.AuditLog;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(indexName = "audit-logs-#{@elasticsearchIndexSuffix}")
@Setting(settingPath = "elasticsearch/settings.json")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String workspaceId;

    @Field(type = FieldType.Keyword)
    private String actorId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String actorEmail;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String actorName;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Keyword)
    private String resourceId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String resourceName;

    @Field(type = FieldType.Object)
    private Map<String, Object> metadata;

    @Field(type = FieldType.Keyword)
    private String ipAddress;

    @Field(type = FieldType.Text)
    private String userAgent;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String severity;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    public static AuditLogDocument fromEntity(AuditLog entity) {
        return AuditLogDocument.builder()
            .id(entity.getId().toString())
            .workspaceId(entity.getWorkspaceId().toString())
            .actorId(entity.getActorId().toString())
            .actorEmail(entity.getActorEmail())
            .actorName(entity.getActorName())
            .action(entity.getAction())
            .resourceType(entity.getResourceType())
            .resourceId(entity.getResourceId().toString())
            .resourceName(entity.getResourceName())
            .ipAddress(entity.getIpAddress())
            .userAgent(entity.getUserAgent())
            .sessionId(entity.getSessionId())
            .severity(entity.getSeverity().name())
            .category(entity.getCategory().name())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
