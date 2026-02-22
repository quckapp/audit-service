package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataExportReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.DATA_EXPORT;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalExports", logs.size());

        Set<String> uniqueExporters = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .map(AuditLog::getActorEmail)
            .collect(Collectors.toSet());
        summary.put("uniqueExporters", uniqueExporters.size());
        summary.put("exporterEmails", uniqueExporters);

        Map<String, Long> exportsByUser = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .collect(Collectors.groupingBy(
                AuditLog::getActorEmail,
                Collectors.counting()
            ));
        summary.put("exportsByUser", exportsByUser);

        Map<String, Long> byResourceType = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getResourceType,
                Collectors.counting()
            ));
        summary.put("exportsByResourceType", byResourceType);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        List<AuditLog> allLogs = auditLogRepository.findByWorkspaceIdAndDateRange(
            context.workspaceId(),
            context.periodStart(),
            context.periodEnd()
        );

        // Filter for export-related actions
        return allLogs.stream()
            .filter(log ->
                log.getAction().contains("EXPORT") ||
                log.getAction().contains("DOWNLOAD") ||
                log.getAction().contains("BULK_"))
            .toList();
    }
}
