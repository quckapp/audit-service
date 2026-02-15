package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.ArchivedAuditLog;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.RetentionPolicy;
import com.quckapp.audit.domain.repository.ArchivedAuditLogRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {

    private final AuditLogRepository auditLogRepository;
    private final ArchivedAuditLogRepository archivedAuditLogRepository;

    @Transactional
    public int archiveAuditLogs(RetentionPolicy policy) {
        Instant cutoffDate = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
        List<AuditLog> logsToArchive = findLogsToArchive(policy, cutoffDate);

        if (logsToArchive.isEmpty()) {
            return 0;
        }

        List<ArchivedAuditLog> archivedLogs = logsToArchive.stream()
            .map(auditLog -> ArchivedAuditLog.fromAuditLog(auditLog, policy.getId()))
            .toList();

        archivedAuditLogRepository.saveAll(archivedLogs);
        log.info("Archived {} audit logs for policy: {}", archivedLogs.size(), policy.getName());

        return archivedLogs.size();
    }

    private List<AuditLog> findLogsToArchive(RetentionPolicy policy, Instant cutoffDate) {
        if (policy.getCategory() != null && policy.getMinSeverity() != null) {
            return auditLogRepository.findByCreatedAtBeforeAndCategoryAndSeverityLessThan(
                cutoffDate, policy.getCategory(), policy.getMinSeverity());
        } else if (policy.getCategory() != null) {
            return auditLogRepository.findByCreatedAtBeforeAndCategory(cutoffDate, policy.getCategory());
        } else if (policy.getMinSeverity() != null) {
            return auditLogRepository.findByCreatedAtBeforeAndSeverityLessThan(cutoffDate, policy.getMinSeverity());
        } else {
            return auditLogRepository.findByCreatedAtBefore(cutoffDate);
        }
    }

    @Transactional(readOnly = true)
    public long getArchivedCountByPolicy(UUID policyId) {
        return archivedAuditLogRepository.countByPolicyId(policyId);
    }
}
