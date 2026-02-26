package com.quckapp.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.GlobalExceptionHandler;
import com.quckapp.audit.exception.ResourceNotFoundException;
import com.quckapp.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {AuditLogControllerTest.TestConfig.class, AuditLogController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class AuditLogControllerTest {

    @EnableWebMvc
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditLogService auditLogService;

    private UUID workspaceId;
    private UUID actorId;
    private UUID resourceId;
    private AuditLogResponse sampleResponse;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        resourceId = UUID.randomUUID();

        sampleResponse = AuditLogResponse.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(actorId)
            .actorEmail("test@example.com")
            .actorName("Test User")
            .action("USER_CREATED")
            .resourceType("USER")
            .resourceId(resourceId)
            .resourceName("Test Resource")
            .severity(AuditSeverity.MEDIUM)
            .category(AuditCategory.DATA_MODIFICATION)
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Create Audit Log")
    class CreateAuditLogTests {

        @Test
        @DisplayName("POST /api/v1/audit/logs - should create audit log")
        void shouldCreateAuditLog() throws Exception {
            CreateAuditLogRequest request = CreateAuditLogRequest.builder()
                .workspaceId(workspaceId)
                .actorId(actorId)
                .actorEmail("test@example.com")
                .action("USER_CREATED")
                .resourceType("USER")
                .resourceId(resourceId)
                .severity(AuditSeverity.MEDIUM)
                .category(AuditCategory.DATA_MODIFICATION)
                .build();

            when(auditLogService.createAuditLog(any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/audit/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Audit log created"))
                .andExpect(jsonPath("$.data.action").value("USER_CREATED"));

            verify(auditLogService).createAuditLog(any());
        }
    }

    @Nested
    @DisplayName("Search Audit Logs")
    class SearchAuditLogsTests {

        @Test
        @DisplayName("POST /api/v1/audit/logs/search - should search audit logs")
        void shouldSearchAuditLogs() throws Exception {
            AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .workspaceId(workspaceId)
                .page(0)
                .size(20)
                .build();

            PagedResponse<AuditLogResponse> pagedResponse = PagedResponse.<AuditLogResponse>builder()
                .content(List.of(sampleResponse))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

            when(auditLogService.searchAuditLogs(any())).thenReturn(pagedResponse);

            mockMvc.perform(post("/api/v1/audit/logs/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("Get Audit Log By ID")
    class GetAuditLogByIdTests {

        @Test
        @DisplayName("GET /api/v1/audit/logs/{id} - should get audit log by id")
        void shouldGetAuditLogById() throws Exception {
            UUID logId = sampleResponse.getId();
            when(auditLogService.getAuditLogById(logId)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/audit/logs/{id}", logId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(logId.toString()));
        }

        @Test
        @DisplayName("GET /api/v1/audit/logs/{id} - should return 404 when not found")
        void shouldReturn404WhenAuditLogNotFound() throws Exception {
            UUID logId = UUID.randomUUID();
            when(auditLogService.getAuditLogById(logId))
                .thenThrow(new ResourceNotFoundException("Audit log not found"));

            mockMvc.perform(get("/api/v1/audit/logs/{id}", logId))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Get Audit Logs By Workspace")
    class GetAuditLogsByWorkspaceTests {

        @Test
        @DisplayName("GET /api/v1/audit/logs/workspace/{workspaceId} - should get logs by workspace")
        void shouldGetAuditLogsByWorkspace() throws Exception {
            PagedResponse<AuditLogResponse> pagedResponse = PagedResponse.<AuditLogResponse>builder()
                .content(List.of(sampleResponse))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

            when(auditLogService.searchAuditLogs(any())).thenReturn(pagedResponse);

            mockMvc.perform(get("/api/v1/audit/logs/workspace/{workspaceId}", workspaceId)
                    .param("page", "0")
                    .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Get Statistics")
    class GetStatisticsTests {

        @Test
        @DisplayName("GET /api/v1/audit/logs/statistics/workspace/{workspaceId} - should get statistics")
        void shouldGetStatistics() throws Exception {
            AuditStatistics statistics = AuditStatistics.builder()
                .workspaceId(workspaceId)
                .periodStart(Instant.now().minusSeconds(86400))
                .periodEnd(Instant.now())
                .totalEvents(100)
                .eventsByAction(Map.of("USER_CREATED", 50L))
                .eventsByCategory(Map.of("DATA_MODIFICATION", 100L))
                .eventsBySeverity(Map.of("MEDIUM", 100L))
                .topActors(List.of())
                .topResources(List.of())
                .build();

            when(auditLogService.getStatistics(eq(workspaceId), any(), any())).thenReturn(statistics);

            mockMvc.perform(get("/api/v1/audit/logs/statistics/workspace/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalEvents").value(100))
                .andExpect(jsonPath("$.data.eventsByAction.USER_CREATED").value(50));
        }
    }
}
