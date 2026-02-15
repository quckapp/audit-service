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
public class AdminActionsReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.ADMIN_ACTIONS;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        Set<String> uniqueAdmins = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .map(AuditLog::getActorEmail)
            .collect(Collectors.toSet());
        summary.put("uniqueAdmins", uniqueAdmins.size());
        summary.put("adminEmails", uniqueAdmins);

        Map<String, Long> actionsByAdmin = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .collect(Collectors.groupingBy(
                AuditLog::getActorEmail,
                Collectors.counting()
            ));
        summary.put("actionsByAdmin", actionsByAdmin);

        Map<String, Long> byAction = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getAction,
                Collectors.counting()
            ));
        summary.put("eventsByAction", byAction);

        long configChanges = logs.stream()
            .filter(log -> log.getCategory() == AuditLog.AuditCategory.CONFIGURATION)
            .count();
        summary.put("configurationChanges", configChanges);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        List<AuditLog> allLogs = auditLogRepository.findByWorkspaceIdAndDateRange(
            context.workspaceId(),
            context.periodStart(),
            context.periodEnd()
        );

        // Filter for admin actions (configuration, security, and authorization changes)
        return allLogs.stream()
            .filter(log ->
                log.getCategory() == AuditLog.AuditCategory.CONFIGURATION ||
                log.getCategory() == AuditLog.AuditCategory.AUTHORIZATION ||
                log.getAction().startsWith("ADMIN_") ||
                log.getAction().contains("ROLE") ||
                log.getAction().contains("PERMISSION"))
            .toList();
    }
}
