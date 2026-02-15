package com.quckapp.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.domain.repository.AuditLogElasticsearchRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogElasticsearchRepository elasticsearchRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    private UUID workspaceId;
    private UUID actorId;
    private UUID resourceId;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        resourceId = UUID.randomUUID();

        sampleAuditLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(actorId)
            .actorEmail("test@example.com")
            .actorName("Test User")
            .action("USER_CREATED")
            .resourceType("USER")
            .resourceId(resourceId)
            .resourceName("Test Resource")
            .ipAddress("192.168.1.1")
            .userAgent("Mozilla/5.0")
            .severity(AuditSeverity.MEDIUM)
            .category(AuditCategory.DATA_MODIFICATION)
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("createAuditLog")
    class CreateAuditLogTests {

        @Test
        @DisplayName("should create audit log successfully")
        void shouldCreateAuditLogSuccessfully() {
            CreateAuditLogRequest request = CreateAuditLogRequest.builder()
                .workspaceId(workspaceId)
                .actorId(actorId)
                .actorEmail("test@example.com")
                .actorName("Test User")
                .action("USER_CREATED")
                .resourceType("USER")
                .resourceId(resourceId)
                .resourceName("Test Resource")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .severity(AuditSeverity.MEDIUM)
                .category(AuditCategory.DATA_MODIFICATION)
                .build();

            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);

            AuditLogResponse response = auditLogService.createAuditLog(request);

            assertThat(response).isNotNull();
            assertThat(response.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(response.getActorId()).isEqualTo(actorId);
            assertThat(response.getAction()).isEqualTo("USER_CREATED");
            assertThat(response.getResourceType()).isEqualTo("USER");
            assertThat(response.getSeverity()).isEqualTo(AuditSeverity.MEDIUM);
            assertThat(response.getCategory()).isEqualTo(AuditCategory.DATA_MODIFICATION);

            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("should create audit log with metadata")
        void shouldCreateAuditLogWithMetadata() throws Exception {
            Map<String, Object> metadata = Map.of("key1", "value1", "key2", 123);

            CreateAuditLogRequest request = CreateAuditLogRequest.builder()
                .workspaceId(workspaceId)
                .actorId(actorId)
                .action("CONFIG_CHANGED")
                .resourceType("SETTINGS")
                .resourceId(resourceId)
                .metadata(metadata)
                .severity(AuditSeverity.HIGH)
                .category(AuditCategory.CONFIGURATION)
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{\"key1\":\"value1\",\"key2\":123}");
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleAuditLog);

            AuditLogResponse response = auditLogService.createAuditLog(request);

            assertThat(response).isNotNull();
            verify(objectMapper).writeValueAsString(metadata);
        }
    }

    @Nested
    @DisplayName("searchAuditLogs")
    class SearchAuditLogsTests {

        @Test
        @DisplayName("should search audit logs by workspace")
        void shouldSearchAuditLogsByWorkspace() {
            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(eq(workspaceId), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getPage()).isZero();
        }

        @Test
        @DisplayName("should search audit logs by actor")
        void shouldSearchAuditLogsByActor() {
            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .actorId(actorId)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdAndActorIdOrderByCreatedAtDesc(
                eq(workspaceId), eq(actorId), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
            verify(auditLogRepository).findByWorkspaceIdAndActorIdOrderByCreatedAtDesc(
                eq(workspaceId), eq(actorId), any(Pageable.class));
        }

        @Test
        @DisplayName("should search audit logs by resource type and id")
        void shouldSearchAuditLogsByResourceTypeAndId() {
            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .resourceType("USER")
                .resourceId(resourceId)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
                eq(workspaceId), eq("USER"), eq(resourceId), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should search audit logs by date range")
        void shouldSearchAuditLogsByDateRange() {
            Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
            Instant endDate = Instant.now();

            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .startDate(startDate)
                .endDate(endDate)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdAndDateRange(
                eq(workspaceId), eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should search audit logs by category")
        void shouldSearchAuditLogsByCategory() {
            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .category(AuditCategory.AUTHENTICATION)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdAndCategory(
                eq(workspaceId), eq(AuditCategory.AUTHENTICATION), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should search audit logs by severities")
        void shouldSearchAuditLogsBySeverities() {
            Set<AuditSeverity> severities = Set.of(AuditSeverity.HIGH, AuditSeverity.CRITICAL);

            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .severities(severities)
                .page(0)
                .size(20)
                .build();

            Page<AuditLog> page = new PageImpl<>(List.of(sampleAuditLog), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findByWorkspaceIdAndSeverityIn(
                eq(workspaceId), anyList(), any(Pageable.class)))
                .thenReturn(page);

            PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(request);

            assertThat(response.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAuditLogById")
    class GetAuditLogByIdTests {

        @Test
        @DisplayName("should return audit log when found")
        void shouldReturnAuditLogWhenFound() {
            UUID logId = sampleAuditLog.getId();
            when(auditLogRepository.findById(logId)).thenReturn(Optional.of(sampleAuditLog));

            AuditLogResponse response = auditLogService.getAuditLogById(logId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(logId);
        }

        @Test
        @DisplayName("should throw exception when audit log not found")
        void shouldThrowExceptionWhenNotFound() {
            UUID logId = UUID.randomUUID();
            when(auditLogRepository.findById(logId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auditLogService.getAuditLogById(logId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Audit log not found");
        }
    }

    @Nested
    @DisplayName("getStatistics")
    class GetStatisticsTests {

        @Test
        @DisplayName("should return statistics for workspace")
        void shouldReturnStatisticsForWorkspace() {
            Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
            Instant endDate = Instant.now();

            List<Object[]> actionCounts = new ArrayList<>();
            actionCounts.add(new Object[]{"USER_CREATED", 50L});
            actionCounts.add(new Object[]{"USER_UPDATED", 50L});

            List<Object[]> categoryCounts = new ArrayList<>();
            categoryCounts.add(new Object[]{AuditCategory.DATA_MODIFICATION, 100L});

            List<Object[]> severityCounts = new ArrayList<>();
            severityCounts.add(new Object[]{AuditSeverity.MEDIUM, 100L});

            List<Object[]> topActors = new ArrayList<>();
            topActors.add(new Object[]{actorId, "test@example.com", "Test User", 100L});

            List<Object[]> topResources = new ArrayList<>();
            topResources.add(new Object[]{"USER", resourceId, "Test Resource", 100L});

            when(auditLogRepository.countByWorkspaceIdAndDateRange(workspaceId, startDate, endDate))
                .thenReturn(100L);
            when(auditLogRepository.countByActionInDateRange(eq(workspaceId), eq(startDate), eq(endDate)))
                .thenReturn(actionCounts);
            when(auditLogRepository.countByCategoryInDateRange(eq(workspaceId), eq(startDate), eq(endDate)))
                .thenReturn(categoryCounts);
            when(auditLogRepository.countBySeverityInDateRange(eq(workspaceId), eq(startDate), eq(endDate)))
                .thenReturn(severityCounts);
            when(auditLogRepository.findTopActorsInDateRange(eq(workspaceId), eq(startDate), eq(endDate), any()))
                .thenReturn(topActors);
            when(auditLogRepository.findTopResourcesInDateRange(eq(workspaceId), eq(startDate), eq(endDate), any()))
                .thenReturn(topResources);

            AuditStatistics statistics = auditLogService.getStatistics(workspaceId, startDate, endDate);

            assertThat(statistics).isNotNull();
            assertThat(statistics.getTotalEvents()).isEqualTo(100);
            assertThat(statistics.getEventsByAction()).containsEntry("USER_CREATED", 50L);
            assertThat(statistics.getEventsByCategory()).containsEntry("DATA_MODIFICATION", 100L);
            assertThat(statistics.getEventsBySeverity()).containsEntry("MEDIUM", 100L);
            assertThat(statistics.getTopActors()).hasSize(1);
            assertThat(statistics.getTopResources()).hasSize(1);
        }

        @Test
        @DisplayName("should use default date range when not provided")
        void shouldUseDefaultDateRangeWhenNotProvided() {
            List<Object[]> emptyObjectArrayList = Collections.emptyList();

            when(auditLogRepository.countByWorkspaceIdAndDateRange(eq(workspaceId), any(), any()))
                .thenReturn(50L);
            when(auditLogRepository.countByActionInDateRange(eq(workspaceId), any(), any()))
                .thenReturn(emptyObjectArrayList);
            when(auditLogRepository.countByCategoryInDateRange(eq(workspaceId), any(), any()))
                .thenReturn(emptyObjectArrayList);
            when(auditLogRepository.countBySeverityInDateRange(eq(workspaceId), any(), any()))
                .thenReturn(emptyObjectArrayList);
            when(auditLogRepository.findTopActorsInDateRange(eq(workspaceId), any(), any(), any()))
                .thenReturn(emptyObjectArrayList);
            when(auditLogRepository.findTopResourcesInDateRange(eq(workspaceId), any(), any(), any()))
                .thenReturn(emptyObjectArrayList);

            AuditStatistics statistics = auditLogService.getStatistics(workspaceId, null, null);

            assertThat(statistics).isNotNull();
            assertThat(statistics.getPeriodStart()).isNotNull();
            assertThat(statistics.getPeriodEnd()).isNotNull();
        }
    }
}
