package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LoginHistoryReportGenerator implements ReportGenerator {

    private final AuditLogRepository auditLogRepository;

    @Override
    public ComplianceReport.ReportType getReportType() {
        return ComplianceReport.ReportType.LOGIN_HISTORY;
    }

    @Override
    public Map<String, Object> generateSummary(ReportContext context) {
        List<AuditLog> logs = generateData(context);

        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", getReportType().name());
        summary.put("periodStart", context.periodStart().toString());
        summary.put("periodEnd", context.periodEnd().toString());
        summary.put("totalEvents", logs.size());

        long successfulLogins = logs.stream()
            .filter(log -> "LOGIN_SUCCESS".equals(log.getAction()))
            .count();
        long failedLogins = logs.stream()
            .filter(log -> "LOGIN_FAILED".equals(log.getAction()))
            .count();
        long logouts = logs.stream()
            .filter(log -> "LOGOUT".equals(log.getAction()))
            .count();

        summary.put("successfulLogins", successfulLogins);
        summary.put("failedLogins", failedLogins);
        summary.put("logouts", logouts);

        Map<String, Long> loginsByUser = new HashMap<>();
        logs.stream()
            .filter(log -> log.getActorEmail() != null)
            .forEach(log -> loginsByUser.merge(log.getActorEmail(), 1L, Long::sum));
        summary.put("loginsByUser", loginsByUser);

        return summary;
    }

    @Override
    public List<AuditLog> generateData(ReportContext context) {
        return auditLogRepository.findByWorkspaceIdAndCategoryAndDateRange(
            context.workspaceId(),
            AuditLog.AuditCategory.AUTHENTICATION,
            context.periodStart(),
            context.periodEnd()
        );
    }
}
