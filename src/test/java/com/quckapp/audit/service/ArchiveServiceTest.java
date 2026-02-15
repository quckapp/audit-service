package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.ArchivedAuditLog;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.domain.entity.RetentionPolicy;
import com.quckapp.audit.domain.repository.ArchivedAuditLogRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ArchivedAuditLogRepository archivedAuditLogRepository;

    @InjectMocks
    private ArchiveService archiveService;

    @Captor
    private ArgumentCaptor<List<ArchivedAuditLog>> archivedLogsCaptor;

    private UUID workspaceId;
    private UUID policyId;
    private RetentionPolicy samplePolicy;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        policyId = UUID.randomUUID();

        samplePolicy = RetentionPolicy.builder()
            .id(policyId)
            .workspaceId(workspaceId)
            .name("Test Policy")
            .retentionDays(90)
            .category(AuditCategory.DATA_ACCESS)
            .minSeverity(AuditSeverity.MEDIUM)
            .enabled(true)
            .archiveBeforeDelete(true)
            .build();

        sampleAuditLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(UUID.randomUUID())
            .actorEmail("test@example.com")
            .actorName("Test User")
            .action("DATA_READ")
            .resourceType("FILE")
            .resourceId(UUID.randomUUID())
            .resourceName("test-file.txt")
            .ipAddress("192.168.1.1")
            .userAgent("Mozilla/5.0")
            .severity(AuditSeverity.LOW)
            .category(AuditCategory.DATA_ACCESS)
            .createdAt(Instant.now().minusSeconds(7776000)) // 90 days ago
            .build();
    }

    @Nested
    @DisplayName("archiveAuditLogs")
    class ArchiveAuditLogsTests {

        @Test
        @DisplayName("should archive logs with category and severity filter")
        void shouldArchiveLogsWithCategoryAndSeverityFilter() {
            List<AuditLog> logsToArchive = List.of(sampleAuditLog);

            when(auditLogRepository.findByCreatedAtBeforeAndCategoryAndSeverityLessThan(
                any(), eq(AuditCategory.DATA_ACCESS), eq(AuditSeverity.MEDIUM)))
                .thenReturn(logsToArchive);
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isEqualTo(1);
            verify(archivedAuditLogRepository).saveAll(archivedLogsCaptor.capture());

            List<ArchivedAuditLog> archived = archivedLogsCaptor.getValue();
            assertThat(archived).hasSize(1);
            assertThat(archived.get(0).getArchivedByPolicyId()).isEqualTo(policyId);
            assertThat(archived.get(0).getActorEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should archive logs with only category filter")
        void shouldArchiveLogsWithOnlyCategoryFilter() {
            samplePolicy.setMinSeverity(null);
            List<AuditLog> logsToArchive = List.of(sampleAuditLog);

            when(auditLogRepository.findByCreatedAtBeforeAndCategory(any(), eq(AuditCategory.DATA_ACCESS)))
                .thenReturn(logsToArchive);
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isEqualTo(1);
            verify(auditLogRepository).findByCreatedAtBeforeAndCategory(any(), eq(AuditCategory.DATA_ACCESS));
        }

        @Test
        @DisplayName("should archive logs with only severity filter")
        void shouldArchiveLogsWithOnlySeverityFilter() {
            samplePolicy.setCategory(null);
            samplePolicy.setMinSeverity(AuditSeverity.HIGH);
            List<AuditLog> logsToArchive = List.of(sampleAuditLog);

            when(auditLogRepository.findByCreatedAtBeforeAndSeverityLessThan(any(), eq(AuditSeverity.HIGH)))
                .thenReturn(logsToArchive);
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isEqualTo(1);
            verify(auditLogRepository).findByCreatedAtBeforeAndSeverityLessThan(any(), eq(AuditSeverity.HIGH));
        }

        @Test
        @DisplayName("should archive all logs when no filters")
        void shouldArchiveAllLogsWhenNoFilters() {
            samplePolicy.setCategory(null);
            samplePolicy.setMinSeverity(null);
            List<AuditLog> logsToArchive = List.of(sampleAuditLog);

            when(auditLogRepository.findByCreatedAtBefore(any()))
                .thenReturn(logsToArchive);
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isEqualTo(1);
            verify(auditLogRepository).findByCreatedAtBefore(any());
        }

        @Test
        @DisplayName("should return zero when no logs to archive")
        void shouldReturnZeroWhenNoLogsToArchive() {
            when(auditLogRepository.findByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of());

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isZero();
            verify(archivedAuditLogRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("should archive multiple logs")
        void shouldArchiveMultipleLogs() {
            AuditLog log2 = AuditLog.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .actorId(UUID.randomUUID())
                .actorEmail("user2@example.com")
                .action("DATA_WRITE")
                .resourceType("FILE")
                .resourceId(UUID.randomUUID())
                .severity(AuditSeverity.LOW)
                .category(AuditCategory.DATA_ACCESS)
                .createdAt(Instant.now().minusSeconds(7776000))
                .build();

            AuditLog log3 = AuditLog.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .actorId(UUID.randomUUID())
                .actorEmail("user3@example.com")
                .action("DATA_DELETE")
                .resourceType("FILE")
                .resourceId(UUID.randomUUID())
                .severity(AuditSeverity.LOW)
                .category(AuditCategory.DATA_ACCESS)
                .createdAt(Instant.now().minusSeconds(7776000))
                .build();

            List<AuditLog> logsToArchive = List.of(sampleAuditLog, log2, log3);

            when(auditLogRepository.findByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(logsToArchive);
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            int count = archiveService.archiveAuditLogs(samplePolicy);

            assertThat(count).isEqualTo(3);
            verify(archivedAuditLogRepository).saveAll(archivedLogsCaptor.capture());

            List<ArchivedAuditLog> archived = archivedLogsCaptor.getValue();
            assertThat(archived).hasSize(3);
            assertThat(archived).allMatch(a -> a.getArchivedByPolicyId().equals(policyId));
        }

        @Test
        @DisplayName("should preserve all audit log fields in archive")
        void shouldPreserveAllFieldsInArchive() {
            sampleAuditLog.setMetadata("{\"key\":\"value\"}");
            sampleAuditLog.setPreviousState("{\"old\":true}");
            sampleAuditLog.setNewState("{\"new\":true}");
            sampleAuditLog.setSessionId("session-123");

            when(auditLogRepository.findByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(sampleAuditLog));
            when(archivedAuditLogRepository.saveAll(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));

            archiveService.archiveAuditLogs(samplePolicy);

            verify(archivedAuditLogRepository).saveAll(archivedLogsCaptor.capture());
            ArchivedAuditLog archived = archivedLogsCaptor.getValue().get(0);

            assertThat(archived.getId()).isEqualTo(sampleAuditLog.getId());
            assertThat(archived.getWorkspaceId()).isEqualTo(sampleAuditLog.getWorkspaceId());
            assertThat(archived.getActorId()).isEqualTo(sampleAuditLog.getActorId());
            assertThat(archived.getActorEmail()).isEqualTo(sampleAuditLog.getActorEmail());
            assertThat(archived.getActorName()).isEqualTo(sampleAuditLog.getActorName());
            assertThat(archived.getAction()).isEqualTo(sampleAuditLog.getAction());
            assertThat(archived.getResourceType()).isEqualTo(sampleAuditLog.getResourceType());
            assertThat(archived.getResourceId()).isEqualTo(sampleAuditLog.getResourceId());
            assertThat(archived.getResourceName()).isEqualTo(sampleAuditLog.getResourceName());
            assertThat(archived.getMetadata()).isEqualTo(sampleAuditLog.getMetadata());
            assertThat(archived.getPreviousState()).isEqualTo(sampleAuditLog.getPreviousState());
            assertThat(archived.getNewState()).isEqualTo(sampleAuditLog.getNewState());
            assertThat(archived.getIpAddress()).isEqualTo(sampleAuditLog.getIpAddress());
            assertThat(archived.getUserAgent()).isEqualTo(sampleAuditLog.getUserAgent());
            assertThat(archived.getSessionId()).isEqualTo(sampleAuditLog.getSessionId());
            assertThat(archived.getSeverity()).isEqualTo(sampleAuditLog.getSeverity());
            assertThat(archived.getCategory()).isEqualTo(sampleAuditLog.getCategory());
            assertThat(archived.getCreatedAt()).isEqualTo(sampleAuditLog.getCreatedAt());
            assertThat(archived.getArchivedByPolicyId()).isEqualTo(policyId);
            assertThat(archived.getArchivedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getArchivedCountByPolicy")
    class GetArchivedCountByPolicyTests {

        @Test
        @DisplayName("should return count of archived logs by policy")
        void shouldReturnCountByPolicy() {
            when(archivedAuditLogRepository.countByPolicyId(policyId)).thenReturn(150L);

            long count = archiveService.getArchivedCountByPolicy(policyId);

            assertThat(count).isEqualTo(150);
            verify(archivedAuditLogRepository).countByPolicyId(policyId);
        }

        @Test
        @DisplayName("should return zero when no archived logs")
        void shouldReturnZeroWhenNoArchivedLogs() {
            when(archivedAuditLogRepository.countByPolicyId(policyId)).thenReturn(0L);

            long count = archiveService.getArchivedCountByPolicy(policyId);

            assertThat(count).isZero();
        }
    }
}
