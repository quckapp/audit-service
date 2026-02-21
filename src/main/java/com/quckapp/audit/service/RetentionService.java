package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.RetentionPolicy;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RetentionService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AuditLogRepository auditLogRepository;

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
        log.info("Starting retention policy execution");
        List<RetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();

        for (RetentionPolicy policy : policies) {
            try {
                applyPolicy(policy);
            } catch (Exception e) {
                log.error("Failed to apply retention policy: {}", policy.getId(), e);
            }
        }
        log.info("Completed retention policy execution");
    }

    private void applyPolicy(RetentionPolicy policy) {
        Instant cutoffDate = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
        int deleted;

        if (policy.getCategory() != null) {
            deleted = auditLogRepository.deleteByCreatedAtBeforeAndCategory(cutoffDate, policy.getCategory());
        } else {
            deleted = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        }

        if (deleted > 0) {
            log.info("Retention policy {} deleted {} audit logs older than {} days",
                policy.getName(), deleted, policy.getRetentionDays());
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
