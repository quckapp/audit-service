package com.quckapp.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.exception.GlobalExceptionHandler;
import com.quckapp.audit.service.RetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {RetentionPolicyControllerTest.TestConfig.class, RetentionPolicyController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class RetentionPolicyControllerTest {

    @EnableWebMvc
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RetentionService retentionService;

    private UUID workspaceId;
    private RetentionPolicyResponse samplePolicyResponse;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();

        samplePolicyResponse = RetentionPolicyResponse.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .name("Test Policy")
            .description("Test retention policy")
            .retentionDays(90)
            .category(AuditCategory.DATA_ACCESS)
            .enabled(true)
            .archiveBeforeDelete(false)
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Create Retention Policy")
    class CreatePolicyTests {

        @Test
        @DisplayName("POST /api/audit/retention-policies - should create policy")
        void shouldCreateRetentionPolicy() throws Exception {
            CreateRetentionPolicyRequest request = CreateRetentionPolicyRequest.builder()
                .workspaceId(workspaceId)
                .name("Test Policy")
                .retentionDays(90)
                .category(AuditCategory.DATA_ACCESS)
                .build();

            when(retentionService.createPolicy(any())).thenReturn(samplePolicyResponse);

            mockMvc.perform(post("/api/audit/retention-policies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Retention policy created"))
                .andExpect(jsonPath("$.data.name").value("Test Policy"));
        }
    }

    @Nested
    @DisplayName("Get Retention Policies")
    class GetPoliciesTests {

        @Test
        @DisplayName("GET /api/audit/retention-policies/workspace/{workspaceId} - should get policies")
        void shouldGetRetentionPolicies() throws Exception {
            when(retentionService.getPoliciesByWorkspace(workspaceId))
                .thenReturn(List.of(samplePolicyResponse));

            mockMvc.perform(get("/api/audit/retention-policies/workspace/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/audit/retention-policies/{id} - should get policy by id")
        void shouldGetRetentionPolicyById() throws Exception {
            UUID policyId = samplePolicyResponse.getId();
            when(retentionService.getPolicyById(policyId)).thenReturn(samplePolicyResponse);

            mockMvc.perform(get("/api/audit/retention-policies/{id}", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(policyId.toString()));
        }
    }

    @Nested
    @DisplayName("Update Retention Policy")
    class UpdatePolicyTests {

        @Test
        @DisplayName("PUT /api/audit/retention-policies/{id} - should update policy")
        void shouldUpdateRetentionPolicy() throws Exception {
            UUID policyId = samplePolicyResponse.getId();
            UpdateRetentionPolicyRequest request = UpdateRetentionPolicyRequest.builder()
                .retentionDays(180)
                .enabled(false)
                .build();

            samplePolicyResponse.setRetentionDays(180);
            samplePolicyResponse.setEnabled(false);

            when(retentionService.updatePolicy(eq(policyId), any())).thenReturn(samplePolicyResponse);

            mockMvc.perform(put("/api/audit/retention-policies/{id}", policyId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.retentionDays").value(180));
        }
    }

    @Nested
    @DisplayName("Delete Retention Policy")
    class DeletePolicyTests {

        @Test
        @DisplayName("DELETE /api/audit/retention-policies/{id} - should delete policy")
        void shouldDeleteRetentionPolicy() throws Exception {
            UUID policyId = UUID.randomUUID();

            mockMvc.perform(delete("/api/audit/retention-policies/{id}", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Retention policy deleted"));

            verify(retentionService).deletePolicy(policyId);
        }
    }

    @Nested
    @DisplayName("Execute Retention Policies")
    class ExecutePolicyTests {

        @Test
        @DisplayName("POST /api/audit/retention-policies/execute - should execute all policies")
        void shouldExecuteAllRetentionPolicies() throws Exception {
            RetentionExecutionResult result = RetentionExecutionResult.builder()
                .totalPoliciesExecuted(2)
                .successfulPolicies(2)
                .failedPolicies(0)
                .details(List.of())
                .executedAt(Instant.now())
                .build();

            when(retentionService.executeAllPolicies()).thenReturn(result);

            mockMvc.perform(post("/api/audit/retention-policies/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPoliciesExecuted").value(2))
                .andExpect(jsonPath("$.data.successfulPolicies").value(2));
        }

        @Test
        @DisplayName("POST /api/audit/retention-policies/{id}/execute - should execute single policy")
        void shouldExecuteSingleRetentionPolicy() throws Exception {
            UUID policyId = UUID.randomUUID();
            PolicyExecutionDetail detail = PolicyExecutionDetail.builder()
                .policyId(policyId)
                .policyName("Test Policy")
                .success(true)
                .deletedCount(50)
                .archivedCount(0)
                .esCleanedCount(50)
                .executedAt(Instant.now())
                .build();

            when(retentionService.executePolicyById(policyId)).thenReturn(detail);

            mockMvc.perform(post("/api/audit/retention-policies/{id}/execute", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.deletedCount").value(50));
        }
    }
}
