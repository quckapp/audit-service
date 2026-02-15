package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.ComplianceReport;

import java.util.List;
import java.util.Map;

public interface ReportGenerator {

    ComplianceReport.ReportType getReportType();

    Map<String, Object> generateSummary(ReportContext context);

    List<AuditLog> generateData(ReportContext context);

    record ReportContext(
        java.util.UUID workspaceId,
        java.time.Instant periodStart,
        java.time.Instant periodEnd,
        Map<String, Object> parameters
    ) {}
}
