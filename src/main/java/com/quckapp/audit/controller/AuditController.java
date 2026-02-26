package com.quckapp.audit.controller;

import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.service.AuditLogService;
import com.quckapp.audit.service.ComplianceReportService;
import com.quckapp.audit.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit logging and compliance APIs")
public class AuditController {

    private final AuditLogService auditLogService;
    private final RetentionService retentionService;
    private final ComplianceReportService reportService;

    // ===== Audit Log Endpoints =====

    @PostMapping("/logs")
    @Operation(summary = "Create audit log entry")
    public ResponseEntity<ApiResponse<AuditLogResponse>> createAuditLog(
            @Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Audit log created", auditLogService.createAuditLog(request)));
    }

    @GetMapping("/logs")
    @Operation(summary = "List audit logs with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        AuditLogSearchRequest request = AuditLogSearchRequest.builder()
            .page(page > 0 ? page - 1 : 0)
            .size(perPage)
            .build();
        return ResponseEntity.ok(ApiResponse.success(auditLogService.searchAuditLogs(request)));
    }

    @PostMapping("/logs/search")
    @Operation(summary = "Search audit logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> searchAuditLogs(
            @Valid @RequestBody AuditLogSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.searchAuditLogs(request)));
    }

    @GetMapping("/logs/workspace/{workspaceId}")
    @Operation(summary = "Get audit logs by workspace")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getAuditLogsByWorkspace(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuditLogSearchRequest request = AuditLogSearchRequest.builder()
            .workspaceId(workspaceId)
            .page(page)
            .size(size)
            .build();
        return ResponseEntity.ok(ApiResponse.success(auditLogService.searchAuditLogs(request)));
    }

    @GetMapping("/statistics/workspace/{workspaceId}")
    @Operation(summary = "Get audit statistics")
    public ResponseEntity<ApiResponse<AuditStatistics>> getStatistics(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getStatistics(workspaceId, startDate, endDate)));
    }

    // ===== Retention Policy Endpoints =====

    @PostMapping("/retention-policies")
    @Operation(summary = "Create retention policy")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> createRetentionPolicy(
            @Valid @RequestBody CreateRetentionPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Retention policy created", retentionService.createPolicy(request)));
    }

    @GetMapping("/retention-policies/workspace/{workspaceId}")
    @Operation(summary = "Get retention policies by workspace")
    public ResponseEntity<ApiResponse<List<RetentionPolicyResponse>>> getRetentionPolicies(
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPoliciesByWorkspace(workspaceId)));
    }

    @GetMapping("/retention-policies/{id}")
    @Operation(summary = "Get retention policy by ID")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> getRetentionPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPolicyById(id)));
    }

    @PutMapping("/retention-policies/{id}")
    @Operation(summary = "Update retention policy")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> updateRetentionPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRetentionPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Retention policy updated", retentionService.updatePolicy(id, request)));
    }

    @DeleteMapping("/retention-policies/{id}")
    @Operation(summary = "Delete retention policy")
    public ResponseEntity<ApiResponse<Void>> deleteRetentionPolicy(@PathVariable UUID id) {
        retentionService.deletePolicy(id);
        return ResponseEntity.ok(ApiResponse.success("Retention policy deleted", null));
    }

    // ===== Compliance Report Endpoints =====

    @PostMapping("/reports")
    @Operation(summary = "Request compliance report")
    public ResponseEntity<ApiResponse<ComplianceReportResponse>> requestReport(
            @Valid @RequestBody CreateReportRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID requestedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Report generation started", reportService.requestReport(request, requestedBy)));
    }

    @GetMapping("/reports/workspace/{workspaceId}")
    @Operation(summary = "Get reports by workspace")
    public ResponseEntity<ApiResponse<PagedResponse<ComplianceReportResponse>>> getReports(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReportsByWorkspace(workspaceId, page, size)));
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "Get report by ID")
    public ResponseEntity<ApiResponse<ComplianceReportResponse>> getReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReportById(id)));
    }
}
