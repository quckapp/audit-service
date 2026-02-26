package com.quckapp.audit.controller;

import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/retention-policies")
@RequiredArgsConstructor
@Tag(name = "Retention Policies", description = "Audit log retention policy management APIs")
public class RetentionPolicyController {

    private final RetentionService retentionService;

    @PostMapping
    @Operation(summary = "Create retention policy")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> createRetentionPolicy(
            @Valid @RequestBody CreateRetentionPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Retention policy created", retentionService.createPolicy(request)));
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get retention policies by workspace")
    public ResponseEntity<ApiResponse<List<RetentionPolicyResponse>>> getRetentionPolicies(
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPoliciesByWorkspace(workspaceId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get retention policy by ID")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> getRetentionPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPolicyById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update retention policy")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> updateRetentionPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRetentionPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Retention policy updated", retentionService.updatePolicy(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete retention policy")
    public ResponseEntity<ApiResponse<Void>> deleteRetentionPolicy(@PathVariable UUID id) {
        retentionService.deletePolicy(id);
        return ResponseEntity.ok(ApiResponse.success("Retention policy deleted", null));
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute all retention policies")
    public ResponseEntity<ApiResponse<RetentionExecutionResult>> executeAllRetentionPolicies() {
        return ResponseEntity.ok(ApiResponse.success("Retention policies executed", retentionService.executeAllPolicies()));
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "Execute single retention policy")
    public ResponseEntity<ApiResponse<PolicyExecutionDetail>> executeRetentionPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Retention policy executed", retentionService.executePolicyById(id)));
    }
}
