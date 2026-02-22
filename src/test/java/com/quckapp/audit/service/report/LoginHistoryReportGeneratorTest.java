package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.service.report.ReportGenerator.ReportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginHistoryReportGeneratorTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private LoginHistoryReportGenerator generator;

    private UUID workspaceId;
    private ReportContext context;
    private AuditLog loginSuccessLog;
    private AuditLog loginFailedLog;
    private AuditLog logoutLog;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        Instant periodStart = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();
        context = new ReportContext(workspaceId, periodStart, periodEnd, null);

        loginSuccessLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(UUID.randomUUID())
            .actorEmail("user1@example.com")
            .action("LOGIN_SUCCESS")
            .resourceType("SESSION")
            .resourceId(UUID.randomUUID())
            .severity(AuditSeverity.LOW)
            .category(AuditCategory.AUTHENTICATION)
            .createdAt(Instant.now())
            .build();

        loginFailedLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(UUID.randomUUID())
            .actorEmail("user2@example.com")
            .action("LOGIN_FAILED")
            .resourceType("SESSION")
            .resourceId(UUID.randomUUID())
            .severity(AuditSeverity.MEDIUM)
            .category(AuditCategory.AUTHENTICATION)
            .createdAt(Instant.now())
            .build();

        logoutLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(UUID.randomUUID())
            .actorEmail("user1@example.com")
            .action("LOGOUT")
            .resourceType("SESSION")
            .resourceId(UUID.randomUUID())
            .severity(AuditSeverity.LOW)
            .category(AuditCategory.AUTHENTICATION)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("should return correct report type")
    void shouldReturnCorrectReportType() {
        assertThat(generator.getReportType()).isEqualTo(ComplianceReport.ReportType.LOGIN_HISTORY);
    }

    @Nested
    @DisplayName("generateData")
    class GenerateDataTests {

        @Test
        @DisplayName("should fetch authentication logs for workspace and period")
        void shouldFetchAuthenticationLogs() {
            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(
                eq(workspaceId), eq(AuditCategory.AUTHENTICATION), any(), any()))
                .thenReturn(List.of(loginSuccessLog, loginFailedLog, logoutLog));

            List<AuditLog> result = generator.generateData(context);

            assertThat(result).hasSize(3);
            verify(auditLogRepository).findByWorkspaceIdAndCategoryAndDateRange(
                eq(workspaceId), eq(AuditCategory.AUTHENTICATION),
                eq(context.periodStart()), eq(context.periodEnd()));
        }

        @Test
        @DisplayName("should return empty list when no logs found")
        void shouldReturnEmptyListWhenNoLogs() {
            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of());

            List<AuditLog> result = generator.generateData(context);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateSummary")
    class GenerateSummaryTests {

        @Test
        @DisplayName("should generate summary with login statistics")
        void shouldGenerateSummaryWithLoginStatistics() {
            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of(loginSuccessLog, loginFailedLog, logoutLog));

            Map<String, Object> summary = generator.generateSummary(context);

            assertThat(summary).containsEntry("reportType", "LOGIN_HISTORY");
            assertThat(summary).containsEntry("totalEvents", 3);
            assertThat(summary).containsEntry("successfulLogins", 1L);
            assertThat(summary).containsEntry("failedLogins", 1L);
            assertThat(summary).containsEntry("logouts", 1L);
        }

        @Test
        @DisplayName("should include logins by user")
        void shouldIncludeLoginsByUser() {
            AuditLog anotherLoginSuccess = AuditLog.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .actorId(UUID.randomUUID())
                .actorEmail("user1@example.com")
                .action("LOGIN_SUCCESS")
                .resourceType("SESSION")
                .resourceId(UUID.randomUUID())
                .severity(AuditSeverity.LOW)
                .category(AuditCategory.AUTHENTICATION)
                .createdAt(Instant.now())
                .build();

            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of(loginSuccessLog, anotherLoginSuccess, loginFailedLog));

            Map<String, Object> summary = generator.generateSummary(context);

            @SuppressWarnings("unchecked")
            Map<String, Long> loginsByUser = (Map<String, Long>) summary.get("loginsByUser");
            assertThat(loginsByUser).containsEntry("user1@example.com", 2L);
            assertThat(loginsByUser).containsEntry("user2@example.com", 1L);
        }

        @Test
        @DisplayName("should include period dates in summary")
        void shouldIncludePeriodDatesInSummary() {
            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of());

            Map<String, Object> summary = generator.generateSummary(context);

            assertThat(summary.get("periodStart")).isEqualTo(context.periodStart().toString());
            assertThat(summary.get("periodEnd")).isEqualTo(context.periodEnd().toString());
        }

        @Test
        @DisplayName("should handle empty logs gracefully")
        void shouldHandleEmptyLogsGracefully() {
            when(auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(List.of());

            Map<String, Object> summary = generator.generateSummary(context);

            assertThat(summary).containsEntry("totalEvents", 0);
            assertThat(summary).containsEntry("successfulLogins", 0L);
            assertThat(summary).containsEntry("failedLogins", 0L);
            assertThat(summary).containsEntry("logouts", 0L);
        }
    }
}
