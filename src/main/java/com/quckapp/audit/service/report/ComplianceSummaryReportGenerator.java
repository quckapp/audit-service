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
public class ComplianceSummaryReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.COMPLIANCE_SUMMARY;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        // Category breakdown
        Map<String, Long> byCategory = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCategory().name(),
                Collectors.counting()
            ));
        summary.put("eventsByCategory", byCategory);

        // Severity breakdown
        Map<String, Long> bySeverity = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getSeverity().name(),
                Collectors.counting()
            ));
        summary.put("eventsBySeverity", bySeverity);

        // Action breakdown
        Map<String, Long> byAction = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getAction,
                Collectors.counting()
            ));
        summary.put("eventsByAction", byAction);

        // User activity summary
        Set<String> uniqueUsers = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .map(AuditLog::getActorEmail)
            .collect(Collectors.toSet());
        summary.put("uniqueUsers", uniqueUsers.size());

        // Security metrics
        long criticalEvents = logs.stream()
            .filter(log -> log.getSeverity() == AuditLog.AuditSeverity.CRITICAL)
            .count();
        long highEvents = logs.stream()
            .filter(log -> log.getSeverity() == AuditLog.AuditSeverity.HIGH)
            .count();
        long securityEvents = logs.stream()
            .filter(log -> log.getCategory() == AuditLog.AuditCategory.SECURITY)
            .count();
        long authEvents = logs.stream()
            .filter(log -> log.getCategory() == AuditLog.AuditCategory.AUTHENTICATION)
            .count();

        summary.put("criticalEvents", criticalEvents);
        summary.put("highSeverityEvents", highEvents);
        summary.put("securityEvents", securityEvents);
        summary.put("authenticationEvents", authEvents);

        // Compliance score (basic calculation)
        double complianceScore = calculateComplianceScore(logs, criticalEvents, highEvents);
        summary.put("complianceScore", complianceScore);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        return auditLogRepository.findByWorkspaceIdAndDateRange(
            context.workspaceId(),
            context.periodStart(),
            context.periodEnd()
        );
    }

    private double calculateComplianceScore(List<AuditLog> logs, long criticalEvents, long highEvents) {
        if (logs.isEmpty()) {
            return 100.0;
        }

        // Base score of 100, deduct for issues
        double score = 100.0;

        // Deduct for critical events (5 points each, max 30)
        score -= Math.min(criticalEvents * 5, 30);

        // Deduct for high severity events (2 points each, max 20)
        score -= Math.min(highEvents * 2, 20);

        return Math.max(score, 0);
    }
}
