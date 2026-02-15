package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SecurityAuditReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.SECURITY_AUDIT;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        Map<String, Long> bySeverity = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getSeverity().name(),
                Collectors.counting()
            ));
        summary.put("eventsBySeverity", bySeverity);

        long criticalEvents = logs.stream()
            .filter(log -> log.getSeverity() == AuditLog.AuditSeverity.CRITICAL)
            .count();
        long highEvents = logs.stream()
            .filter(log -> log.getSeverity() == AuditLog.AuditSeverity.HIGH)
            .count();

        summary.put("criticalEvents", criticalEvents);
        summary.put("highSeverityEvents", highEvents);

        Map<String, Long> byAction = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getAction,
                Collectors.counting()
            ));
        summary.put("eventsByAction", byAction);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        return auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(
            context.workspaceId(),
            AuditLog.AuditCategory.SECURITY,
            context.periodStart(),
            context.periodEnd()
        );
    }
}
