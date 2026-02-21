package com.quckapp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.domain.repository.ComplianceReportRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
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

            report.setSummary(toJson(summary));
            report.setStatus(ComplianceReport.ReportStatus.COMPLETED);
            report.setCompletedAt(Instant.now());
            reportRepository.save(report);

            log.info("Completed compliance report: {}", reportId);
        } catch (Exception e) {
            log.error("Failed to generate compliance report: {}", reportId, e);
            reportRepository.findById(reportId).ifPresent(report -> {
                report.setStatus(ComplianceReport.ReportStatus.FAILED);
                report.setErrorMessage(e.getMessage());
                reportRepository.save(report);
            });
        }
    }

    private Map<String, Object> generateReportData(ComplianceReport report) {
        Map<String, Object> summary = new HashMap<>();

        long totalEvents = auditLogRepository.countByWorkspaceIdAndDateRange(
            report.getWorkspaceId(), report.getPeriodStart(), report.getPeriodEnd());

        summary.put("totalEvents", totalEvents);
        summary.put("periodStart", report.getPeriodStart().toString());
        summary.put("periodEnd", report.getPeriodEnd().toString());
        summary.put("reportType", report.getReportType().name());

        var actionCounts = auditLogRepository.countByActionInDateRange(
            report.getWorkspaceId(), report.getPeriodStart(), report.getPeriodEnd());

        Map<String, Long> actionBreakdown = new HashMap<>();
        for (Object[] row : actionCounts) {
            actionBreakdown.put((String) row[0], (Long) row[1]);
        }
        summary.put("actionBreakdown", actionBreakdown);

        return summary;
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
