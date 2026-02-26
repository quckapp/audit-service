package com.quckapp.audit.service;

import com.opencsv.CSVWriter;
import com.quckapp.audit.domain.entity.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CsvExportService {

    @Value("${audit.reports.export-path:./exports}")
    private String exportPath;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("UTC"));

    public ExportResult exportToCsv(List<AuditLog> auditLogs, String reportName, UUID reportId) throws IOException {
        Path exportDir = Paths.get(exportPath);
        if (!Files.exists(exportDir)) {
            Files.createDirectories(exportDir);
        }

        String filename = String.format("%s_%s_%d.csv",
            sanitizeFilename(reportName),
            reportId.toString().substring(0, 8),
            System.currentTimeMillis());

        Path filePath = exportDir.resolve(filename);
        File file = filePath.toFile();

        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Write header
            String[] header = {
                "ID", "Workspace ID", "Actor ID", "Actor Email", "Actor Name",
                "Action", "Resource Type", "Resource ID", "Resource Name",
                "IP Address", "User Agent", "Session ID",
                "Severity", "Category", "Created At"
            };
            writer.writeNext(header);

            // Write data rows
            for (AuditLog log : auditLogs) {
                String[] row = {
                    log.getId().toString(),
                    log.getWorkspaceId().toString(),
                    log.getActorId().toString(),
                    log.getActorEmail(),
                    log.getActorName(),
                    log.getAction(),
                    log.getResourceType(),
                    log.getResourceId().toString(),
                    log.getResourceName(),
                    log.getIpAddress(),
                    log.getUserAgent(),
                    log.getSessionId(),
                    log.getSeverity().name(),
                    log.getCategory().name(),
                    formatInstant(log.getCreatedAt())
                };
                writer.writeNext(row);
            }
        }

        long fileSize = Files.size(filePath);
        String fileUrl = "/api/v1/audit/reports/" + reportId + "/download";

        log.info("Exported {} audit logs to CSV: {} ({} bytes)", auditLogs.size(), filename, fileSize);

        return new ExportResult(filePath.toString(), fileUrl, fileSize);
    }

    public Path getExportFilePath(String filename) {
        return Paths.get(exportPath).resolve(filename);
    }

    public boolean exportExists(String filename) {
        Path filePath = Paths.get(exportPath).resolve(filename);
        return Files.exists(filePath);
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_")
            .toLowerCase()
            .substring(0, Math.min(name.length(), 50));
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DATE_FORMATTER.format(instant);
    }

    public record ExportResult(String filePath, String fileUrl, long fileSize) {}
}
