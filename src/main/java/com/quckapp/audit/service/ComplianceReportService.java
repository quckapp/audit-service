package com.quckapp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.domain.repository.ComplianceReportRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.ResourceNotFoundException;
import com.quckapp.audit.service.report.ReportGenerator;
import com.quckapp.audit.service.report.ReportGeneratorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceReportService {

    private final ComplianceReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final ReportGeneratorFactory reportGeneratorFactory;
    private final CsvExportService csvExportService;

    public ComplianceReportResponse requestReport(CreateReportRequest request, UUID requestedBy) {
        ComplianceReport report = ComplianceReport.builder()
            .workspaceId(request.getWorkspaceId())
            .name(request.getName())
            .reportType(request.getReportType())
            .status(ComplianceReport.ReportStatus.PENDING)
            .periodStart(request.getPeriodStart())
            .periodEnd(request.getPeriodEnd())
            .requestedBy(requestedBy)
            .parameters(toJson(request.getParameters()))
            .build();

        report = reportRepository.save(report);
        log.info("Created compliance report request: {} for workspace {}", report.getId(), report.getWorkspaceId());

        // Generate report asynchronously
        generateReportAsync(report.getId());

        return mapToResponse(report);
    }

    @Async
    public void generateReportAsync(UUID reportId) {
        try {
            ComplianceReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

            report.setStatus(ComplianceReport.ReportStatus.PROCESSING);
            reportRepository.save(report);

            // Generate report based on type
            Map<String, Object> summary = generateReportData(report);

            // Get report data for CSV export
            List<AuditLog> reportData = getReportDataInternal(report);

            // Export to CSV
            CsvExportService.ExportResult exportResult = csvExportService.exportToCsv(
                reportData, report.getName(), report.getId());

            report.setSummary(toJson(summary));
            report.setFileUrl(exportResult.fileUrl());
            report.setFileSize(exportResult.fileSize());
            report.setStatus(ComplianceReport.ReportStatus.COMPLETED);
            report.setCompletedAt(Instant.now());
            reportRepository.save(report);

            log.info("Completed compliance report: {} with {} records exported", reportId, reportData.size());
        } catch (Exception e) {
            log.error("Failed to generate compliance report: {}", reportId, e);
            reportRepository.findById(reportId).ifPresent(report -> {
                report.setStatus(ComplianceReport.ReportStatus.FAILED);
                report.setErrorMessage(e.getMessage());
                reportRepository.save(report);
            });
        }
    }

    private List<AuditLog> getReportDataInternal(ComplianceReport report) {
        ReportGenerator generator = reportGeneratorFactory.getGenerator(report.getReportType());
        ReportGenerator.ReportContext context = new ReportGenerator.ReportContext(
            report.getWorkspaceId(),
            report.getPeriodStart(),
            report.getPeriodEnd(),
            fromJson(report.getParameters(), Map.class)
        );
        return generator.generateData(context);
    }

    private Map<String, Object> generateReportData(ComplianceReport report) {
        ReportGenerator generator = reportGeneratorFactory.getGenerator(report.getReportType());
        ReportGenerator.ReportContext context = new ReportGenerator.ReportContext(
            report.getWorkspaceId(),
            report.getPeriodStart(),
            report.getPeriodEnd(),
            fromJson(report.getParameters(), Map.class)
        );
        return generator.generateSummary(context);
    }

    public List<AuditLog> getReportData(UUID reportId) {
        ComplianceReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        ReportGenerator generator = reportGeneratorFactory.getGenerator(report.getReportType());
        ReportGenerator.ReportContext context = new ReportGenerator.ReportContext(
            report.getWorkspaceId(),
            report.getPeriodStart(),
            report.getPeriodEnd(),
            fromJson(report.getParameters(), Map.class)
        );
        return generator.generateData(context);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplianceReportResponse> getReportsByWorkspace(UUID workspaceId, int page, int size) {
        Page<ComplianceReport> reports = reportRepository.findByWorkspaceIdOrderByCreatedAtDesc(
            workspaceId, PageRequest.of(page, size));

        return PagedResponse.<ComplianceReportResponse>builder()
            .content(reports.getContent().stream().map(this::mapToResponse).toList())
            .page(reports.getNumber())
            .size(reports.getSize())
            .totalElements(reports.getTotalElements())
            .totalPages(reports.getTotalPages())
            .first(reports.isFirst())
            .last(reports.isLast())
            .build();
    }

    @Transactional(readOnly = true)
    public ComplianceReportResponse getReportById(UUID id) {
        ComplianceReport report = reportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        return mapToResponse(report);
    }

    @SuppressWarnings("unchecked")
    private ComplianceReportResponse mapToResponse(ComplianceReport report) {
        return ComplianceReportResponse.builder()
            .id(report.getId())
            .workspaceId(report.getWorkspaceId())
            .name(report.getName())
            .reportType(report.getReportType())
            .status(report.getStatus())
            .periodStart(report.getPeriodStart())
            .periodEnd(report.getPeriodEnd())
            .requestedBy(report.getRequestedBy())
            .parameters(fromJson(report.getParameters(), Map.class))
            .summary(fromJson(report.getSummary(), Map.class))
            .fileUrl(report.getFileUrl())
            .fileSize(report.getFileSize())
            .errorMessage(report.getErrorMessage())
            .createdAt(report.getCreatedAt())
            .completedAt(report.getCompletedAt())
            .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize from JSON", e);
            return null;
        }
    }
}
