package com.quckapp.audit.controller;

import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Audit log management APIs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    @Operation(summary = "Create audit log entry")
    public ResponseEntity<ApiResponse<AuditLogResponse>> createAuditLog(
            @Valid @RequestBody CreateAuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Audit log created", auditLogService.createAuditLog(request)));
    }

    @PostMapping("/search")
    @Operation(summary = "Search audit logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> searchAuditLogs(
            @Valid @RequestBody AuditLogSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.searchAuditLogs(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log by ID")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getAuditLogById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getAuditLogById(id)));
    }

    @GetMapping("/workspace/{workspaceId}")
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
    @Operation(summary = "Get audit statistics for workspace")
    public ResponseEntity<ApiResponse<AuditStatistics>> getStatistics(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getStatistics(workspaceId, startDate, endDate)));
    }
}
