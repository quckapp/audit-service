package com.quckapp.audit.controller;

import com.quckapp.audit.dto.AuditDtos.*;
import com.quckapp.audit.service.ComplianceReportService;
import com.quckapp.audit.service.CsvExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/reports")
@RequiredArgsConstructor
@Tag(name = "Compliance Reports", description = "Compliance report generation and download APIs")
public class ComplianceReportController {

    private final ComplianceReportService reportService;
    private final CsvExportService csvExportService;

    @PostMapping
    @Operation(summary = "Request compliance report")
    public ResponseEntity<ApiResponse<ComplianceReportResponse>> requestReport(
            @Valid @RequestBody CreateReportRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID requestedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Report generation started", reportService.requestReport(request, requestedBy)));
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get reports by workspace")
    public ResponseEntity<ApiResponse<PagedResponse<ComplianceReportResponse>>> getReports(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReportsByWorkspace(workspaceId, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get report by ID")
    public ResponseEntity<ApiResponse<ComplianceReportResponse>> getReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReportById(id)));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download report CSV file")
    public ResponseEntity<Resource> downloadReport(@PathVariable UUID id) {
        ComplianceReportResponse report = reportService.getReportById(id);

        if (report.getFileUrl() == null || report.getFileUrl().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String filename = String.format("%s_%s.csv",
            report.getName().replaceAll("[^a-zA-Z0-9-_]", "_").toLowerCase(),
            id.toString().substring(0, 8));

        try {
            Path exportDir = csvExportService.getExportFilePath("").getParent();
            if (exportDir == null) {
                exportDir = Path.of("./exports");
            }

            Path[] matchingFiles = Files.list(exportDir)
                .filter(p -> p.getFileName().toString().contains(id.toString().substring(0, 8)))
                .toArray(Path[]::new);

            if (matchingFiles.length == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Path filePath = matchingFiles[0];
            Resource resource = new FileSystemResource(filePath);

            if (!resource.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(report.getFileSize())
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
