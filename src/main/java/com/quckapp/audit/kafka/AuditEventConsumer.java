package com.quckapp.audit.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.dto.AuditDtos.CreateAuditLogRequest;
import com.quckapp.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.audit-events:audit-events}", groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void consumeAuditEvent(String message) {
        try {
            CreateAuditLogRequest request = objectMapper.readValue(message, CreateAuditLogRequest.class);
            auditLogService.createAuditLog(request);
            log.debug("Processed audit event: {} on {}", request.getAction(), request.getResourceType());
        } catch (Exception e) {
            log.error("Failed to process audit event: {}", message, e);
        }
    }

    @KafkaListener(topics = "${kafka.topics.user-events:user-events}", groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void consumeUserEvent(String message) {
        try {
            log.debug("Received user event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            String eventType = getStringField(event, "eventType");
            if (eventType == null) {
                log.warn("User event missing eventType: {}", message);
                return;
            }

            CreateAuditLogRequest request = mapUserEventToAuditLog(event, eventType);
            if (request != null) {
                auditLogService.createAuditLog(request);
                log.debug("Processed user event: {} for user {}", eventType, request.getActorId());
            }
        } catch (Exception e) {
            log.error("Failed to process user event: {}", message, e);
        }
    }

    @KafkaListener(topics = "${kafka.topics.auth-events:auth-events}", groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void consumeAuthEvent(String message) {
        try {
            log.debug("Received auth event: {}", message);
            JsonNode event = objectMapper.readTree(message);

            String eventType = getStringField(event, "eventType");
            if (eventType == null) {
                log.warn("Auth event missing eventType: {}", message);
                return;
            }

            CreateAuditLogRequest request = mapAuthEventToAuditLog(event, eventType);
            if (request != null) {
                auditLogService.createAuditLog(request);
                log.debug("Processed auth event: {} for user {}", eventType, request.getActorId());
            }
        } catch (Exception e) {
            log.error("Failed to process auth event: {}", message, e);
        }
    }

    private CreateAuditLogRequest mapUserEventToAuditLog(JsonNode event, String eventType) {
        UserEventMapping mapping = getUserEventMapping(eventType);
        if (mapping == null) {
            log.debug("Unknown user event type: {}", eventType);
            return null;
        }

        UUID workspaceId = getUuidField(event, "workspaceId");
        UUID userId = getUuidField(event, "userId");
        UUID actorId = getUuidField(event, "actorId");
        if (actorId == null) actorId = userId;

        if (workspaceId == null || userId == null) {
            log.warn("User event missing required fields workspaceId or userId");
            return null;
        }

        return CreateAuditLogRequest.builder()
            .workspaceId(workspaceId)
            .actorId(actorId)
            .actorEmail(getStringField(event, "actorEmail"))
            .actorName(getStringField(event, "actorName"))
            .action(mapping.action)
            .resourceType("USER")
            .resourceId(userId)
            .resourceName(getStringField(event, "userName"))
            .metadata(extractMetadata(event))
            .ipAddress(getStringField(event, "ipAddress"))
            .userAgent(getStringField(event, "userAgent"))
            .sessionId(getStringField(event, "sessionId"))
            .severity(mapping.severity)
            .category(mapping.category)
            .build();
    }

    private CreateAuditLogRequest mapAuthEventToAuditLog(JsonNode event, String eventType) {
        AuthEventMapping mapping = getAuthEventMapping(eventType);
        if (mapping == null) {
            log.debug("Unknown auth event type: {}", eventType);
            return null;
        }

        UUID workspaceId = getUuidField(event, "workspaceId");
        UUID userId = getUuidField(event, "userId");
        UUID actorId = getUuidField(event, "actorId");
        if (actorId == null) actorId = userId;

        if (workspaceId == null || userId == null) {
            log.warn("Auth event missing required fields workspaceId or userId");
            return null;
        }

        return CreateAuditLogRequest.builder()
            .workspaceId(workspaceId)
            .actorId(actorId)
            .actorEmail(getStringField(event, "email"))
            .actorName(getStringField(event, "name"))
            .action(mapping.action)
            .resourceType("USER")
            .resourceId(userId)
            .resourceName(getStringField(event, "email"))
            .metadata(extractMetadata(event))
            .ipAddress(getStringField(event, "ipAddress"))
            .userAgent(getStringField(event, "userAgent"))
            .sessionId(getStringField(event, "sessionId"))
            .severity(mapping.severity)
            .category(mapping.category)
            .build();
    }

    private UserEventMapping getUserEventMapping(String eventType) {
        return switch (eventType) {
            case "USER_CREATED" -> new UserEventMapping(
                "USER_CREATED", AuditLog.AuditCategory.DATA_MODIFICATION, AuditLog.AuditSeverity.MEDIUM);
            case "USER_UPDATED" -> new UserEventMapping(
                "USER_UPDATED", AuditLog.AuditCategory.DATA_MODIFICATION, AuditLog.AuditSeverity.LOW);
            case "USER_DEACTIVATED" -> new UserEventMapping(
                "USER_DEACTIVATED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.HIGH);
            case "PROFILE_UPDATED" -> new UserEventMapping(
                "PROFILE_UPDATED", AuditLog.AuditCategory.DATA_MODIFICATION, AuditLog.AuditSeverity.LOW);
            case "USER_DELETED" -> new UserEventMapping(
                "USER_DELETED", AuditLog.AuditCategory.DATA_MODIFICATION, AuditLog.AuditSeverity.HIGH);
            case "USER_RESTORED" -> new UserEventMapping(
                "USER_RESTORED", AuditLog.AuditCategory.DATA_MODIFICATION, AuditLog.AuditSeverity.MEDIUM);
            default -> null;
        };
    }

    private AuthEventMapping getAuthEventMapping(String eventType) {
        return switch (eventType) {
            case "USER_REGISTERED" -> new AuthEventMapping(
                "USER_REGISTERED", AuditLog.AuditCategory.AUTHENTICATION, AuditLog.AuditSeverity.MEDIUM);
            case "LOGIN_SUCCESS" -> new AuthEventMapping(
                "LOGIN_SUCCESS", AuditLog.AuditCategory.AUTHENTICATION, AuditLog.AuditSeverity.LOW);
            case "LOGIN_FAILED" -> new AuthEventMapping(
                "LOGIN_FAILED", AuditLog.AuditCategory.AUTHENTICATION, AuditLog.AuditSeverity.MEDIUM);
            case "LOGOUT" -> new AuthEventMapping(
                "LOGOUT", AuditLog.AuditCategory.AUTHENTICATION, AuditLog.AuditSeverity.LOW);
            case "PASSWORD_CHANGED" -> new AuthEventMapping(
                "PASSWORD_CHANGED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.MEDIUM);
            case "PASSWORD_RESET_REQUESTED" -> new AuthEventMapping(
                "PASSWORD_RESET_REQUESTED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.MEDIUM);
            case "PASSWORD_RESET_COMPLETED" -> new AuthEventMapping(
                "PASSWORD_RESET_COMPLETED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.MEDIUM);
            case "USER_BANNED" -> new AuthEventMapping(
                "USER_BANNED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.CRITICAL);
            case "USER_UNBANNED" -> new AuthEventMapping(
                "USER_UNBANNED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.HIGH);
            case "ROLE_CHANGED" -> new AuthEventMapping(
                "ROLE_CHANGED", AuditLog.AuditCategory.AUTHORIZATION, AuditLog.AuditSeverity.HIGH);
            case "PERMISSION_GRANTED" -> new AuthEventMapping(
                "PERMISSION_GRANTED", AuditLog.AuditCategory.AUTHORIZATION, AuditLog.AuditSeverity.MEDIUM);
            case "PERMISSION_REVOKED" -> new AuthEventMapping(
                "PERMISSION_REVOKED", AuditLog.AuditCategory.AUTHORIZATION, AuditLog.AuditSeverity.HIGH);
            case "TOKEN_REFRESHED" -> new AuthEventMapping(
                "TOKEN_REFRESHED", AuditLog.AuditCategory.AUTHENTICATION, AuditLog.AuditSeverity.LOW);
            case "MFA_ENABLED" -> new AuthEventMapping(
                "MFA_ENABLED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.MEDIUM);
            case "MFA_DISABLED" -> new AuthEventMapping(
                "MFA_DISABLED", AuditLog.AuditCategory.SECURITY, AuditLog.AuditSeverity.HIGH);
            default -> null;
        };
    }

    private String getStringField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    private UUID getUuidField(JsonNode node, String fieldName) {
        String value = getStringField(node, fieldName);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(JsonNode event) {
        try {
            JsonNode metadata = event.get("metadata");
            if (metadata != null && !metadata.isNull()) {
                return objectMapper.convertValue(metadata, Map.class);
            }
            // Include some fields as metadata if no explicit metadata
            return Map.of(
                "eventSource", getStringField(event, "source") != null ? getStringField(event, "source") : "kafka",
                "eventTimestamp", getStringField(event, "timestamp") != null ? getStringField(event, "timestamp") : ""
            );
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record UserEventMapping(String action, AuditLog.AuditCategory category, AuditLog.AuditSeverity severity) {}
    private record AuthEventMapping(String action, AuditLog.AuditCategory category, AuditLog.AuditSeverity severity) {}
}
