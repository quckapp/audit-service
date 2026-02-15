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
public class AccessLogReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.ACCESS_LOG;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        Set<String> uniqueUsers = logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .map(AuditLog::getActorEmail)
            .collect(Collectors.toSet());
        summary.put("uniqueUsers", uniqueUsers.size());

        Map<String, Long> byResourceType = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getResourceType,
                Collectors.counting()
            ));
        summary.put("accessByResourceType", byResourceType);

        Map<String, Long> byAction = logs.stream()
            .collect(Collectors.groupingBy(
                AuditLog::getAction,
                Collectors.counting()
            ));
        summary.put("eventsByAction", byAction);

        long reads = logs.stream()
            .filter(log -> log.getAction().contains("READ") || log.getAction().contains("VIEW") || log.getAction().contains("GET"))
            .count();
        long writes = logs.stream()
            .filter(log -> log.getAction().contains("WRITE") || log.getAction().contains("CREATE") ||
                          log.getAction().contains("UPDATE") || log.getAction().contains("DELETE"))
            .count();
        summary.put("readOperations", reads);
        summary.put("writeOperations", writes);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        return auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(
            context.workspaceId(),
            AuditLog.AuditCategory.DATA_ACCESS,
            context.periodStart(),
            context.periodEnd()
        );
    }
}
