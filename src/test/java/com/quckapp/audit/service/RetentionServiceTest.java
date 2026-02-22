package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.domain.entity.RetentionPolicy;
import com.quckapp.audit.domain.repository.AuditLogElasticsearchRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.domain.repository.RetentionPolicyRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.DuplicateResourceException;
import com.quckapp.audit.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    private RetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogElasticsearchRepository elasticsearchRepository;

    @Mock
    private ArchiveService archiveService;

    @InjectMocks
    private RetentionService retentionService;

    private UUID workspaceId;
    private UUID policyId;
    private RetentionPolicy samplePolicy;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        policyId = UUID.randomUUID();

        samplePolicy = RetentionPolicy.builder()
            .id(policyId)
            .workspaceId(workspaceId)
            .name("Test Policy")
            .description("Test retention policy")
            .retentionDays(90)
            .category(AuditCategory.DATA_ACCESS)
            .minSeverity(AuditSeverity.LOW)
            .enabled(true)
            .archiveBeforeDelete(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("createPolicy")
    class CreatePolicyTests {

        @Test
        @DisplayName("should create retention policy successfully")
        void shouldCreatePolicySuccessfully() {
            CreateRetentionPolicyRequest request = CreateRetentionPolicyRequest.builder()
                .workspaceId(workspaceId)
                .name("New Policy")
                .description("New retention policy")
                .retentionDays(30)
                .category(AuditCategory.AUTHENTICATION)
                .minSeverity(AuditSeverity.MEDIUM)
                .archiveBeforeDelete(true)
                .build();

            when(retentionPolicyRepository.existsByWorkspaceIdAndName(workspaceId, "New Policy"))
                .thenReturn(false);
            when(retentionPolicyRepository.save(any(RetentionPolicy.class)))
                .thenAnswer(inv -> {
                    RetentionPolicy p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    p.setCreatedAt(Instant.now());
                    p.setUpdatedAt(Instant.now());
                    return p;
                });

            RetentionPolicyResponse response = retentionService.createPolicy(request);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("New Policy");
            assertThat(response.getRetentionDays()).isEqualTo(30);
            assertThat(response.getCategory()).isEqualTo(AuditCategory.AUTHENTICATION);
            assertThat(response.isEnabled()).isTrue();
            assertThat(response.isArchiveBeforeDelete()).isTrue();

            verify(retentionPolicyRepository).save(any(RetentionPolicy.class));
        }

        @Test
        @DisplayName("should throw exception when policy name already exists")
        void shouldThrowExceptionWhenNameExists() {
            CreateRetentionPolicyRequest request = CreateRetentionPolicyRequest.builder()
                .workspaceId(workspaceId)
                .name("Existing Policy")
                .retentionDays(30)
                .build();

            when(retentionPolicyRepository.existsByWorkspaceIdAndName(workspaceId, "Existing Policy"))
                .thenReturn(true);

            assertThatThrownBy(() -> retentionService.createPolicy(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Retention policy with this name already exists");

            verify(retentionPolicyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getPoliciesByWorkspace")
    class GetPoliciesByWorkspaceTests {

        @Test
        @DisplayName("should return all policies for workspace")
        void shouldReturnAllPoliciesForWorkspace() {
            RetentionPolicy policy2 = RetentionPolicy.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .name("Policy 2")
                .retentionDays(60)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

            when(retentionPolicyRepository.findByWorkspaceId(workspaceId))
                .thenReturn(List.of(samplePolicy, policy2));

            List<RetentionPolicyResponse> responses = retentionService.getPoliciesByWorkspace(workspaceId);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getName()).isEqualTo("Test Policy");
            assertThat(responses.get(1).getName()).isEqualTo("Policy 2");
        }

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyListWhenNoPolicies() {
            when(retentionPolicyRepository.findByWorkspaceId(workspaceId))
                .thenReturn(List.of());

            List<RetentionPolicyResponse> responses = retentionService.getPoliciesByWorkspace(workspaceId);

            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPolicyById")
    class GetPolicyByIdTests {

        @Test
        @DisplayName("should return policy when found")
        void shouldReturnPolicyWhenFound() {
            when(retentionPolicyRepository.findById(policyId))
                .thenReturn(Optional.of(samplePolicy));

            RetentionPolicyResponse response = retentionService.getPolicyById(policyId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(policyId);
            assertThat(response.getName()).isEqualTo("Test Policy");
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(retentionPolicyRepository.findById(unknownId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> retentionService.getPolicyById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Retention policy not found");
        }
    }

    @Nested
    @DisplayName("updatePolicy")
    class UpdatePolicyTests {

        @Test
        @DisplayName("should update policy fields")
        void shouldUpdatePolicyFields() {
            UpdateRetentionPolicyRequest request = UpdateRetentionPolicyRequest.builder()
                .name("Updated Policy")
                .description("Updated description")
                .retentionDays(180)
                .minSeverity(AuditSeverity.HIGH)
                .enabled(false)
                .archiveBeforeDelete(true)
                .build();

            when(retentionPolicyRepository.findById(policyId))
                .thenReturn(Optional.of(samplePolicy));
            when(retentionPolicyRepository.save(any(RetentionPolicy.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            RetentionPolicyResponse response = retentionService.updatePolicy(policyId, request);

            assertThat(response.getName()).isEqualTo("Updated Policy");
            assertThat(response.getDescription()).isEqualTo("Updated description");
            assertThat(response.getRetentionDays()).isEqualTo(180);
            assertThat(response.getMinSeverity()).isEqualTo(AuditSeverity.HIGH);
            assertThat(response.isEnabled()).isFalse();
            assertThat(response.isArchiveBeforeDelete()).isTrue();
        }

        @Test
        @DisplayName("should update only provided fields")
        void shouldUpdateOnlyProvidedFields() {
            UpdateRetentionPolicyRequest request = UpdateRetentionPolicyRequest.builder()
                .retentionDays(60)
                .build();

            when(retentionPolicyRepository.findById(policyId))
                .thenReturn(Optional.of(samplePolicy));
            when(retentionPolicyRepository.save(any(RetentionPolicy.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            RetentionPolicyResponse response = retentionService.updatePolicy(policyId, request);

            assertThat(response.getName()).isEqualTo("Test Policy"); // Unchanged
            assertThat(response.getRetentionDays()).isEqualTo(60); // Updated
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenPolicyNotFound() {
            UUID unknownId = UUID.randomUUID();
            UpdateRetentionPolicyRequest request = UpdateRetentionPolicyRequest.builder()
                .name("Updated")
                .build();

            when(retentionPolicyRepository.findById(unknownId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> retentionService.updatePolicy(unknownId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deletePolicy")
    class DeletePolicyTests {

        @Test
        @DisplayName("should delete policy when exists")
        void shouldDeletePolicyWhenExists() {
            when(retentionPolicyRepository.existsById(policyId)).thenReturn(true);

            retentionService.deletePolicy(policyId);

            verify(retentionPolicyRepository).deleteById(policyId);
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(retentionPolicyRepository.existsById(unknownId)).thenReturn(false);

            assertThatThrownBy(() -> retentionService.deletePolicy(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Retention policy not found");

            verify(retentionPolicyRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("executeAllPolicies")
    class ExecuteAllPoliciesTests {

        @Test
        @DisplayName("should execute all enabled policies")
        void shouldExecuteAllEnabledPolicies() {
            RetentionPolicy policy2 = RetentionPolicy.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .name("Policy 2")
                .retentionDays(60)
                .enabled(true)
                .archiveBeforeDelete(false)
                .build();

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy, policy2));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(5);
            when(auditLogRepository.findIdsByCreatedAtBefore(any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBefore(any()))
                .thenReturn(3);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            assertThat(result.getTotalPoliciesExecuted()).isEqualTo(2);
            assertThat(result.getSuccessfulPolicies()).isEqualTo(2);
            assertThat(result.getFailedPolicies()).isZero();
            assertThat(result.getDetails()).hasSize(2);
        }

        @Test
        @DisplayName("should handle policy execution failure")
        void shouldHandlePolicyExecutionFailure() {
            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenThrow(new RuntimeException("Database error"));

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            assertThat(result.getTotalPoliciesExecuted()).isEqualTo(1);
            assertThat(result.getSuccessfulPolicies()).isZero();
            assertThat(result.getFailedPolicies()).isEqualTo(1);
            assertThat(result.getDetails().get(0).isSuccess()).isFalse();
            assertThat(result.getDetails().get(0).getErrorMessage()).contains("Database error");
        }

        @Test
        @DisplayName("should archive before delete when configured")
        void shouldArchiveBeforeDeleteWhenConfigured() {
            samplePolicy.setArchiveBeforeDelete(true);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(archiveService.archiveAuditLogs(samplePolicy)).thenReturn(10);
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(10);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(archiveService).archiveAuditLogs(samplePolicy);
            assertThat(result.getDetails().get(0).getArchivedCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("should not archive when not configured")
        void shouldNotArchiveWhenNotConfigured() {
            samplePolicy.setArchiveBeforeDelete(false);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(5);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(archiveService, never()).archiveAuditLogs(any());
            assertThat(result.getDetails().get(0).getArchivedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("executePolicyById")
    class ExecutePolicyByIdTests {

        @Test
        @DisplayName("should execute single policy by id")
        void shouldExecuteSinglePolicyById() {
            when(retentionPolicyRepository.findById(policyId))
                .thenReturn(Optional.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(5);

            PolicyExecutionDetail result = retentionService.executePolicyById(policyId);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPolicyId()).isEqualTo(policyId);
            assertThat(result.getPolicyName()).isEqualTo("Test Policy");
            assertThat(result.getDeletedCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenPolicyNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(retentionPolicyRepository.findById(unknownId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> retentionService.executePolicyById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Severity Filtering")
    class SeverityFilteringTests {

        @Test
        @DisplayName("should delete with category and severity filter")
        void shouldDeleteWithCategoryAndSeverityFilter() {
            samplePolicy.setCategory(AuditCategory.DATA_ACCESS);
            samplePolicy.setMinSeverity(AuditSeverity.MEDIUM);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(3);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(auditLogRepository).deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(
                any(), eq(AuditCategory.DATA_ACCESS), eq(AuditSeverity.MEDIUM));
            assertThat(result.getDetails().get(0).getDeletedCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should delete with only category filter")
        void shouldDeleteWithOnlyCategoryFilter() {
            samplePolicy.setCategory(AuditCategory.AUTHENTICATION);
            samplePolicy.setMinSeverity(null);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategory(any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategory(any(), any()))
                .thenReturn(7);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(auditLogRepository).deleteByCreatedAtBeforeAndCategory(
                any(), eq(AuditCategory.AUTHENTICATION));
            assertThat(result.getDetails().get(0).getDeletedCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("should delete with only severity filter")
        void shouldDeleteWithOnlySeverityFilter() {
            samplePolicy.setCategory(null);
            samplePolicy.setMinSeverity(AuditSeverity.HIGH);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndSeverityLessThan(any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBeforeAndSeverityLessThan(any(), any()))
                .thenReturn(4);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(auditLogRepository).deleteByCreatedAtBeforeAndSeverityLessThan(
                any(), eq(AuditSeverity.HIGH));
        }

        @Test
        @DisplayName("should delete without filters when none specified")
        void shouldDeleteWithoutFiltersWhenNoneSpecified() {
            samplePolicy.setCategory(null);
            samplePolicy.setMinSeverity(null);

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBefore(any()))
                .thenReturn(List.of(UUID.randomUUID()));
            when(auditLogRepository.deleteByCreatedAtBefore(any()))
                .thenReturn(10);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(auditLogRepository).deleteByCreatedAtBefore(any());
        }
    }

    @Nested
    @DisplayName("Elasticsearch Cleanup")
    class ElasticsearchCleanupTests {

        @Test
        @DisplayName("should cleanup elasticsearch after delete")
        void shouldCleanupElasticsearchAfterDelete() {
            List<UUID> idsToDelete = List.of(UUID.randomUUID(), UUID.randomUUID());

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(idsToDelete);
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(2);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(elasticsearchRepository).deleteAllById(anyList());
            assertThat(result.getDetails().get(0).getEsCleanedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should handle elasticsearch cleanup failure gracefully")
        void shouldHandleEsCleanupFailureGracefully() {
            List<UUID> idsToDelete = List.of(UUID.randomUUID());

            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(idsToDelete);
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(1);
            doThrow(new RuntimeException("ES unavailable")).when(elasticsearchRepository).deleteAllById(anyList());

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            // Should still succeed even if ES cleanup fails
            assertThat(result.getDetails().get(0).isSuccess()).isTrue();
            assertThat(result.getDetails().get(0).getEsCleanedCount()).isZero();
        }

        @Test
        @DisplayName("should skip elasticsearch cleanup when no ids to delete")
        void shouldSkipEsCleanupWhenNoIdsToDelete() {
            when(retentionPolicyRepository.findByEnabledTrue())
                .thenReturn(List.of(samplePolicy));
            when(auditLogRepository.findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(List.of());
            when(auditLogRepository.deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(any(), any(), any()))
                .thenReturn(0);

            RetentionExecutionResult result = retentionService.executeAllPolicies();

            verify(elasticsearchRepository, never()).deleteAllById(anyList());
        }
    }
}
