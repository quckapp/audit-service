package com.quckapp.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.GlobalExceptionHandler;
import com.quckapp.audit.service.ComplianceReportService;
import com.quckapp.audit.service.CsvExportService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {ComplianceReportControllerTest.TestConfig.class, ComplianceReportController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class ComplianceReportControllerTest {

    @EnableWebMvc
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplianceReportService reportService;

    @MockBean
    private CsvExportService csvExportService;

    private UUID workspaceId;
    private ComplianceReportResponse sampleReportResponse;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();

        sampleReportResponse = ComplianceReportResponse.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .name("Login History Report")
            .reportType(ComplianceReport.ReportType.LOGIN_HISTORY)
            .status(ComplianceReport.ReportStatus.COMPLETED)
            .periodStart(Instant.now().minusSeconds(86400 * 7))
            .periodEnd(Instant.now())
            .summary(Map.of("totalEvents", 100))
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Request Report")
    class RequestReportTests {

        @Test
        @DisplayName("POST /api/v1/audit/reports - should create report request")
        void shouldRequestReport() throws Exception {
            CreateReportRequest request = CreateReportRequest.builder()
                .workspaceId(workspaceId)
                .name("Login History Report")
                .reportType(ComplianceReport.ReportType.LOGIN_HISTORY)
                .periodStart(Instant.now().minusSeconds(86400 * 7))
                .periodEnd(Instant.now())
                .build();

            sampleReportResponse.setStatus(ComplianceReport.ReportStatus.PENDING);
            when(reportService.requestReport(any(), any())).thenReturn(sampleReportResponse);

            mockMvc.perform(post("/api/v1/audit/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Report generation started"))
                .andExpect(jsonPath("$.data.name").value("Login History Report"));
        }
    }

    @Nested
    @DisplayName("Get Reports")
    class GetReportsTests {

        @Test
        @DisplayName("GET /api/v1/audit/reports/workspace/{workspaceId} - should get reports")
        void shouldGetReportsByWorkspace() throws Exception {
            PagedResponse<ComplianceReportResponse> pagedResponse = PagedResponse.<ComplianceReportResponse>builder()
                .content(List.of(sampleReportResponse))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

            when(reportService.getReportsByWorkspace(eq(workspaceId), anyInt(), anyInt()))
                .thenReturn(pagedResponse);

            mockMvc.perform(get("/api/v1/audit/reports/workspace/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/v1/audit/reports/{id} - should get report by id")
        void shouldGetReportById() throws Exception {
            UUID reportId = sampleReportResponse.getId();
            when(reportService.getReportById(reportId)).thenReturn(sampleReportResponse);

            mockMvc.perform(get("/api/v1/audit/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Download Report")
    class DownloadReportTests {

        @Test
        @DisplayName("GET /api/v1/audit/reports/{id}/download - should return 404 when no file")
        void shouldReturn404WhenNoFile() throws Exception {
            UUID reportId = sampleReportResponse.getId();
            sampleReportResponse.setFileUrl(null);
            when(reportService.getReportById(reportId)).thenReturn(sampleReportResponse);

            mockMvc.perform(get("/api/v1/audit/reports/{id}/download", reportId))
                .andExpect(status().isNotFound());
        }
    }
}
