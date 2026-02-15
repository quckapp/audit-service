package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.RetentionPolicy;
import com.quckapp.audit.domain.repository.AuditLogElasticsearchRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.domain.repository.RetentionPolicyRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.DuplicateResourceException;
import com.quckapp.audit.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RetentionService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogElasticsearchRepository elasticsearchRepository;
    private final ArchiveService archiveService;

    public RetentionPolicyResponse createPolicy(CreateRetentionPolicyRequest request) {
        if (retentionPolicyRepository.existsByWorkspaceIdAndName(request.getWorkspaceId(), request.getName())) {
            throw new DuplicateResourceException("Retention policy with this name already exists");
        }

        RetentionPolicy policy = RetentionPolicy.builder()
            .workspaceId(request.getWorkspaceId())
            .name(request.getName())
            .description(request.getDescription())
            .retentionDays(request.getRetentionDays())
            .category(request.getCategory())
            .minSeverity(request.getMinSeverity())
            .enabled(true)
            .archiveBeforeDelete(request.isArchiveBeforeDelete())
            .build();

        policy = retentionPolicyRepository.save(policy);
        log.info("Created retention policy: {} for workspace {}", policy.getName(), policy.getWorkspaceId());
        return mapToResponse(policy);
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicyResponse> getPoliciesByWorkspace(UUID workspaceId) {
        return retentionPolicyRepository.findByWorkspaceId(workspaceId).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RetentionPolicyResponse getPolicyById(UUID id) {
        RetentionPolicy policy = retentionPolicyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Retention policy not found"));
        return mapToResponse(policy);
    }

    public RetentionPolicyResponse updatePolicy(UUID id, UpdateRetentionPolicyRequest request) {
        RetentionPolicy policy = retentionPolicyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Retention policy not found"));

        if (request.getName() != null) policy.setName(request.getName());
        if (request.getDescription() != null) policy.setDescription(request.getDescription());
        if (request.getRetentionDays() != null) policy.setRetentionDays(request.getRetentionDays());
        if (request.getMinSeverity() != null) policy.setMinSeverity(request.getMinSeverity());
        if (request.getEnabled() != null) policy.setEnabled(request.getEnabled());
        if (request.getArchiveBeforeDelete() != null) policy.setArchiveBeforeDelete(request.getArchiveBeforeDelete());

        policy = retentionPolicyRepository.save(policy);
        return mapToResponse(policy);
    }

    public void deletePolicy(UUID id) {
        if (!retentionPolicyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Retention policy not found");
        }
        retentionPolicyRepository.deleteById(id);
    }

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void applyRetentionPolicies() {
        log.info("Starting scheduled retention policy execution");
        executeAllPolicies();
        log.info("Completed scheduled retention policy execution");
    }

    public RetentionExecutionResult executeAllPolicies() {
        List<RetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();
        List<PolicyExecutionDetail> details = new ArrayList<>();
        int successful = 0;
        int failed = 0;

        for (RetentionPolicy policy : policies) {
            PolicyExecutionDetail detail = executePolicy(policy);
            details.add(detail);
            if (detail.isSuccess()) {
                successful++;
            } else {
                failed++;
            }
        }

        return RetentionExecutionResult.builder()
            .totalPoliciesExecuted(policies.size())
            .successfulPolicies(successful)
            .failedPolicies(failed)
            .details(details)
            .executedAt(Instant.now())
            .build();
    }

    public PolicyExecutionDetail executePolicyById(UUID policyId) {
        RetentionPolicy policy = retentionPolicyRepository.findById(policyId)
            .orElseThrow(() -> new ResourceNotFoundException("Retention policy not found"));
        return executePolicy(policy);
    }

    private PolicyExecutionDetail executePolicy(RetentionPolicy policy) {
        Instant executedAt = Instant.now();
        try {
            Instant cutoffDate = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
            int archived = 0;
            int deleted = 0;
            int esCleaned = 0;

            // Get IDs for ES cleanup before deletion
            List<UUID> idsToDelete = findIdsToDelete(policy, cutoffDate);

            // Archive if configured
            if (policy.isArchiveBeforeDelete()) {
                archived = archiveService.archiveAuditLogs(policy);
            }

            // Delete from MySQL with severity filtering
            deleted = deleteAuditLogs(policy, cutoffDate);

            // Clean up Elasticsearch
            esCleaned = cleanupElasticsearch(idsToDelete);

            log.info("Retention policy {} executed: archived={}, deleted={}, esClean={}",
                policy.getName(), archived, deleted, esCleaned);

            return PolicyExecutionDetail.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .success(true)
                .archivedCount(archived)
                .deletedCount(deleted)
                .esCleanedCount(esCleaned)
                .executedAt(executedAt)
                .build();
        } catch (Exception e) {
            log.error("Failed to apply retention policy: {}", policy.getId(), e);
            return PolicyExecutionDetail.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .success(false)
                .errorMessage(e.getMessage())
                .executedAt(executedAt)
                .build();
        }
    }

    private List<UUID> findIdsToDelete(RetentionPolicy policy, Instant cutoffDate) {
        if (policy.getCategory() != null && policy.getMinSeverity() != null) {
            return auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(
                cutoffDate, policy.getCategory(), policy.getMinSeverity());
        } else if (policy.getCategory() != null) {
            return auditLogRepository.findIdsByCreatedAtBeforeAndCategory(cutoffDate, policy.getCategory());
        } else if (policy.getMinSeverity() != null) {
            return auditLogRepository.findIdsByCreatedAtBeforeAndSeverityLessThan(cutoffDate, policy.getMinSeverity());
        } else {
            return auditLogRepository.findIdsByCreatedAtBefore(cutoffDate);
        }
    }

    private int deleteAuditLogs(RetentionPolicy policy, Instant cutoffDate) {
        if (policy.getCategory() != null && policy.getMinSeverity() != null) {
            return auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(
                cutoffDate, policy.getCategory(), policy.getMinSeverity());
        } else if (policy.getCategory() != null) {
            return auditLogRepository.deleteByCreatedAtBeforeAndCategory(cutoffDate, policy.getCategory());
        } else if (policy.getMinSeverity() != null) {
            return auditLogRepository.deleteByCreatedAtBeforeAndSeverityLessThan(cutoffDate, policy.getMinSeverity());
        } else {
            return auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        }
    }

    private int cleanupElasticsearch(List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        try {
            List<String> stringIds = ids.stream().map(UUID::toString).toList();
            elasticsearchRepository.deleteAllById(stringIds);
            return ids.size();
        } catch (Exception e) {
            log.warn("Failed to cleanup Elasticsearch, logs may be orphaned: {}", e.getMessage());
            return 0;
        }
    }

    private RetentionPolicyResponse mapToResponse(RetentionPolicy policy) {
        return RetentionPolicyResponse.builder()
            .id(policy.getId())
            .workspaceId(policy.getWorkspaceId())
            .name(policy.getName())
            .description(policy.getDescription())
            .retentionDays(policy.getRetentionDays())
            .category(policy.getCategory())
            .minSeverity(policy.getMinSeverity())
            .enabled(policy.isEnabled())
            .archiveBeforeDelete(policy.isArchiveBeforeDelete())
            .createdAt(policy.getCreatedAt())
            .updatedAt(policy.getUpdatedAt())
            .build();
    }
}
