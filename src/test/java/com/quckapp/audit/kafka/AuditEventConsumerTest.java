package com.quckapp.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.dto.AuditDtos.CreateAuditLogRequest;
import com.quckapp.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditEventConsumer consumer;

    @Captor
    private ArgumentCaptor<CreateAuditLogRequest> requestCaptor;

    private UUID workspaceId;
    private UUID userId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("consumeAuditEvent")
    class ConsumeAuditEventTests {

        @Test
        @DisplayName("should process direct audit event")
        void shouldProcessDirectAuditEvent() throws Exception {
            CreateAuditLogRequest request = CreateAuditLogRequest.builder()
                .workspaceId(workspaceId)
                .actorId(actorId)
                .actorEmail("test@example.com")
                .action("DIRECT_ACTION")
                .resourceType("DOCUMENT")
                .resourceId(UUID.randomUUID())
                .severity(AuditSeverity.MEDIUM)
                .category(AuditCategory.DATA_ACCESS)
                .build();

            String message = objectMapper.writeValueAsString(request);

            consumer.consumeAuditEvent(message);

            verify(auditLogService).createAuditLog(any(CreateAuditLogRequest.class));
        }

        @Test
        @DisplayName("should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() {
            consumer.consumeAuditEvent("invalid json");

            verify(auditLogService, never()).createAuditLog(any());
        }
    }

    @Nested
    @DisplayName("consumeUserEvent")
    class ConsumeUserEventTests {

        @Test
        @DisplayName("should process USER_CREATED event")
        void shouldProcessUserCreatedEvent() {
            String message = String.format("""
                {
                    "eventType": "USER_CREATED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s",
                    "actorEmail": "admin@example.com",
                    "actorName": "Admin User",
                    "userName": "New User",
                    "ipAddress": "192.168.1.1",
                    "userAgent": "Mozilla/5.0"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeUserEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            CreateAuditLogRequest captured = requestCaptor.getValue();

            assertThat(captured.getAction()).isEqualTo("USER_CREATED");
            assertThat(captured.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(captured.getActorId()).isEqualTo(actorId);
            assertThat(captured.getResourceType()).isEqualTo("USER");
            assertThat(captured.getResourceId()).isEqualTo(userId);
            assertThat(captured.getCategory()).isEqualTo(AuditCategory.DATA_MODIFICATION);
            assertThat(captured.getSeverity()).isEqualTo(AuditSeverity.MEDIUM);
        }

        @Test
        @DisplayName("should process USER_UPDATED event")
        void shouldProcessUserUpdatedEvent() {
            String message = String.format("""
                {
                    "eventType": "USER_UPDATED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeUserEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("USER_UPDATED");
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.LOW);
        }

        @Test
        @DisplayName("should process USER_DEACTIVATED event with HIGH severity")
        void shouldProcessUserDeactivatedEvent() {
            String message = String.format("""
                {
                    "eventType": "USER_DEACTIVATED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeUserEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("USER_DEACTIVATED");
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.HIGH);
            assertThat(requestCaptor.getValue().getCategory()).isEqualTo(AuditCategory.SECURITY);
        }

        @Test
        @DisplayName("should use userId as actorId when actorId is not provided")
        void shouldUseUserIdAsActorIdWhenNotProvided() {
            String message = String.format("""
                {
                    "eventType": "PROFILE_UPDATED",
                    "workspaceId": "%s",
                    "userId": "%s"
                }
                """, workspaceId, userId);

            consumer.consumeUserEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getActorId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should skip event when eventType is missing")
        void shouldSkipWhenEventTypeMissing() {
            String message = String.format("""
                {
                    "workspaceId": "%s",
                    "userId": "%s"
                }
                """, workspaceId, userId);

            consumer.consumeUserEvent(message);

            verify(auditLogService, never()).createAuditLog(any());
        }

        @Test
        @DisplayName("should skip event when workspaceId is missing")
        void shouldSkipWhenWorkspaceIdMissing() {
            String message = String.format("""
                {
                    "eventType": "USER_CREATED",
                    "userId": "%s"
                }
                """, userId);

            consumer.consumeUserEvent(message);

            verify(auditLogService, never()).createAuditLog(any());
        }

        @Test
        @DisplayName("should skip unknown event types")
        void shouldSkipUnknownEventTypes() {
            String message = String.format("""
                {
                    "eventType": "UNKNOWN_EVENT",
                    "workspaceId": "%s",
                    "userId": "%s"
                }
                """, workspaceId, userId);

            consumer.consumeUserEvent(message);

            verify(auditLogService, never()).createAuditLog(any());
        }

        @Test
        @DisplayName("should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() {
            consumer.consumeUserEvent("invalid json {{{");

            verify(auditLogService, never()).createAuditLog(any());
        }
    }

    @Nested
    @DisplayName("consumeAuthEvent")
    class ConsumeAuthEventTests {

        @Test
        @DisplayName("should process LOGIN_SUCCESS event")
        void shouldProcessLoginSuccessEvent() {
            String message = String.format("""
                {
                    "eventType": "LOGIN_SUCCESS",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "email": "user@example.com",
                    "name": "Test User",
                    "ipAddress": "10.0.0.1"
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            CreateAuditLogRequest captured = requestCaptor.getValue();

            assertThat(captured.getAction()).isEqualTo("LOGIN_SUCCESS");
            assertThat(captured.getCategory()).isEqualTo(AuditCategory.AUTHENTICATION);
            assertThat(captured.getSeverity()).isEqualTo(AuditSeverity.LOW);
            assertThat(captured.getActorEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("should process LOGIN_FAILED event with MEDIUM severity")
        void shouldProcessLoginFailedEvent() {
            String message = String.format("""
                {
                    "eventType": "LOGIN_FAILED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "email": "user@example.com"
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("LOGIN_FAILED");
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.MEDIUM);
        }

        @Test
        @DisplayName("should process PASSWORD_CHANGED event")
        void shouldProcessPasswordChangedEvent() {
            String message = String.format("""
                {
                    "eventType": "PASSWORD_CHANGED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("PASSWORD_CHANGED");
            assertThat(requestCaptor.getValue().getCategory()).isEqualTo(AuditCategory.SECURITY);
        }

        @Test
        @DisplayName("should process USER_BANNED event with CRITICAL severity")
        void shouldProcessUserBannedEvent() {
            String message = String.format("""
                {
                    "eventType": "USER_BANNED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("USER_BANNED");
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.CRITICAL);
            assertThat(requestCaptor.getValue().getCategory()).isEqualTo(AuditCategory.SECURITY);
        }

        @Test
        @DisplayName("should process ROLE_CHANGED event with AUTHORIZATION category")
        void shouldProcessRoleChangedEvent() {
            String message = String.format("""
                {
                    "eventType": "ROLE_CHANGED",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "actorId": "%s"
                }
                """, workspaceId, userId, actorId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("ROLE_CHANGED");
            assertThat(requestCaptor.getValue().getCategory()).isEqualTo(AuditCategory.AUTHORIZATION);
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.HIGH);
        }

        @Test
        @DisplayName("should process MFA_DISABLED event with HIGH severity")
        void shouldProcessMfaDisabledEvent() {
            String message = String.format("""
                {
                    "eventType": "MFA_DISABLED",
                    "workspaceId": "%s",
                    "userId": "%s"
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getAction()).isEqualTo("MFA_DISABLED");
            assertThat(requestCaptor.getValue().getSeverity()).isEqualTo(AuditSeverity.HIGH);
        }

        @Test
        @DisplayName("should skip unknown auth event types")
        void shouldSkipUnknownAuthEventTypes() {
            String message = String.format("""
                {
                    "eventType": "UNKNOWN_AUTH_EVENT",
                    "workspaceId": "%s",
                    "userId": "%s"
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService, never()).createAuditLog(any());
        }

        @Test
        @DisplayName("should handle missing required fields")
        void shouldHandleMissingRequiredFields() {
            String message = """
                {
                    "eventType": "LOGIN_SUCCESS"
                }
                """;

            consumer.consumeAuthEvent(message);

            verify(auditLogService, never()).createAuditLog(any());
        }

        @Test
        @DisplayName("should extract metadata from event")
        void shouldExtractMetadataFromEvent() {
            String message = String.format("""
                {
                    "eventType": "LOGIN_SUCCESS",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "metadata": {
                        "browser": "Chrome",
                        "device": "Desktop"
                    }
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getMetadata()).containsEntry("browser", "Chrome");
            assertThat(requestCaptor.getValue().getMetadata()).containsEntry("device", "Desktop");
        }

        @Test
        @DisplayName("should include session information")
        void shouldIncludeSessionInformation() {
            String message = String.format("""
                {
                    "eventType": "LOGIN_SUCCESS",
                    "workspaceId": "%s",
                    "userId": "%s",
                    "sessionId": "session-xyz-123",
                    "userAgent": "Mozilla/5.0",
                    "ipAddress": "192.168.1.100"
                }
                """, workspaceId, userId);

            consumer.consumeAuthEvent(message);

            verify(auditLogService).createAuditLog(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getSessionId()).isEqualTo("session-xyz-123");
            assertThat(requestCaptor.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(requestCaptor.getValue().getIpAddress()).isEqualTo("192.168.1.100");
        }
    }
}
