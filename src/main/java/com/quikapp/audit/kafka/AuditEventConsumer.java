package com.quikapp.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quikapp.audit.dto.AuditDtos.CreateAuditLogRequest;
import com.quikapp.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
            // Parse and create audit log for user events
        } catch (Exception e) {
            log.error("Failed to process user event: {}", message, e);
        }
    }

    @KafkaListener(topics = "${kafka.topics.auth-events:auth-events}", groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void consumeAuthEvent(String message) {
        try {
            log.debug("Received auth event: {}", message);
            // Parse and create audit log for auth events
        } catch (Exception e) {
            log.error("Failed to process auth event: {}", message, e);
        }
    }
}
