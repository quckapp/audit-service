package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserActivityReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.USER_ACTIVITY;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        Set<UUID> uniqueUsers = logs.stream()
            .map(AuditLog::getActorId)
            .collect(Collectors.toSet());
        summary.put("uniqueUsers", uniqueUsers.size());

        Map<String, Long> activityByUser = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .collect(Collectors.groupingBy(
                AuditLog::getActorEmail,
                Collectors.counting()
            ));
        summary.put("activityByUser", activityByUser);

        Map<String, Long> byAction = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getAction,
                Collectors.counting()
            ));
        summary.put("eventsByAction", byAction);

        Map<String, Long> byCategory = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCategory().name(),
                Collectors.counting()
            ));
        summary.put("eventsByCategory", byCategory);

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
}
