package com.quckapp.audit.service;

import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.entity.AuditLog.AuditCategory;
import com.quckapp.audit.domain.entity.AuditLog.AuditSeverity;
import com.quckapp.audit.service.CsvExportService.ExportResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CsvExportServiceTest {

    private CsvExportService csvExportService;

    @TempDir
    Path tempDir;

    private UUID workspaceId;
    private UUID reportId;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        csvExportService = new CsvExportService();
        ReflectionTestUtils.setField(csvExportService, "exportPath", tempDir.toString());

        workspaceId = UUID.randomUUID();
        reportId = UUID.randomUUID();

        sampleAuditLog = AuditLog.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .actorId(UUID.randomUUID())
            .actorEmail("test@example.com")
            .actorName("Test User")
            .action("USER_LOGIN")
            .resourceType("SESSION")
            .resourceId(UUID.randomUUID())
            .resourceName("User Session")
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0")
            .sessionId("session-abc123")
            .severity(AuditSeverity.LOW)
            .category(AuditCategory.AUTHENTICATION)
            .createdAt(Instant.parse("2024-01-15T10:30:00Z"))
            .build();
    }

    @Nested
    @DisplayName("exportToCsv")
    class ExportToCsvTests {

        @Test
        @DisplayName("should export audit logs to CSV file")
        void shouldExportAuditLogsToCsv() throws IOException {
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Test Report", reportId);

            assertThat(result).isNotNull();
            assertThat(result.filePath()).isNotBlank();
            assertThat(result.fileUrl()).contains(reportId.toString());
            assertThat(result.fileSize()).isGreaterThan(0);

            Path exportedFile = Path.of(result.filePath());
            assertThat(Files.exists(exportedFile)).isTrue();
        }

        @Test
        @DisplayName("should include header row in CSV")
        void shouldIncludeHeaderRow() throws IOException {
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Test Report", reportId);

            String content = Files.readString(Path.of(result.filePath()));
            assertThat(content).contains("ID");
            assertThat(content).contains("Workspace ID");
            assertThat(content).contains("Actor ID");
            assertThat(content).contains("Actor Email");
            assertThat(content).contains("Action");
            assertThat(content).contains("Resource Type");
            assertThat(content).contains("Severity");
            assertThat(content).contains("Category");
            assertThat(content).contains("Created At");
        }

        @Test
        @DisplayName("should include audit log data in CSV")
        void shouldIncludeAuditLogData() throws IOException {
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Test Report", reportId);

            String content = Files.readString(Path.of(result.filePath()));
            assertThat(content).contains("test@example.com");
            assertThat(content).contains("Test User");
            assertThat(content).contains("USER_LOGIN");
            assertThat(content).contains("SESSION");
            assertThat(content).contains("192.168.1.100");
            assertThat(content).contains("LOW");
            assertThat(content).contains("AUTHENTICATION");
        }

        @Test
        @DisplayName("should export multiple audit logs")
        void shouldExportMultipleAuditLogs() throws IOException {
            AuditLog log2 = AuditLog.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .actorId(UUID.randomUUID())
                .actorEmail("user2@example.com")
                .actorName("User Two")
                .action("USER_LOGOUT")
                .resourceType("SESSION")
                .resourceId(UUID.randomUUID())
                .severity(AuditSeverity.LOW)
                .category(AuditCategory.AUTHENTICATION)
                .createdAt(Instant.now())
                .build();

            List<AuditLog> logs = List.of(sampleAuditLog, log2);

            ExportResult result = csvExportService.exportToCsv(logs, "Multi Log Report", reportId);

            String content = Files.readString(Path.of(result.filePath()));
            assertThat(content).contains("test@example.com");
            assertThat(content).contains("user2@example.com");
            assertThat(content).contains("USER_LOGIN");
            assertThat(content).contains("USER_LOGOUT");
        }

        @Test
        @DisplayName("should create export directory if not exists")
        void shouldCreateExportDirectoryIfNotExists() throws IOException {
            Path newExportDir = tempDir.resolve("new_exports");
            ReflectionTestUtils.setField(csvExportService, "exportPath", newExportDir.toString());

            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Test Report", reportId);

            assertThat(Files.exists(newExportDir)).isTrue();
            assertThat(Files.exists(Path.of(result.filePath()))).isTrue();
        }

        @Test
        @DisplayName("should sanitize report name in filename")
        void shouldSanitizeReportNameInFilename() throws IOException {
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Report with spaces & special!chars", reportId);

            String filename = Path.of(result.filePath()).getFileName().toString();
            assertThat(filename).doesNotContain(" ");
            assertThat(filename).doesNotContain("&");
            assertThat(filename).doesNotContain("!");
            assertThat(filename).contains("report_with_spaces");
        }

        @Test
        @DisplayName("should handle empty audit logs list")
        void shouldHandleEmptyAuditLogsList() throws IOException {
            List<AuditLog> logs = List.of();

            ExportResult result = csvExportService.exportToCsv(logs, "Empty Report", reportId);

            assertThat(result.fileSize()).isGreaterThan(0); // At least header row
            String content = Files.readString(Path.of(result.filePath()));
            // Should have header but no data rows
            String[] lines = content.split("\n");
            assertThat(lines.length).isEqualTo(1); // Only header
        }

        @Test
        @DisplayName("should format date correctly in CSV")
        void shouldFormatDateCorrectlyInCsv() throws IOException {
            sampleAuditLog.setCreatedAt(Instant.parse("2024-06-15T14:30:45Z"));
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Date Test", reportId);

            String content = Files.readString(Path.of(result.filePath()));
            assertThat(content).contains("2024-06-15 14:30:45");
        }

        @Test
        @DisplayName("should return correct file URL")
        void shouldReturnCorrectFileUrl() throws IOException {
            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "URL Test", reportId);

            assertThat(result.fileUrl()).isEqualTo("/api/audit/reports/" + reportId + "/download");
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() throws IOException {
            sampleAuditLog.setResourceName(null);
            sampleAuditLog.setSessionId(null);
            sampleAuditLog.setUserAgent(null);

            List<AuditLog> logs = List.of(sampleAuditLog);

            ExportResult result = csvExportService.exportToCsv(logs, "Null Fields Test", reportId);

            assertThat(result.fileSize()).isGreaterThan(0);
            // Should not throw exception
        }
    }

    @Nested
    @DisplayName("exportExists")
    class ExportExistsTests {

        @Test
        @DisplayName("should return true when export file exists")
        void shouldReturnTrueWhenFileExists() throws IOException {
            // Create a test file
            Path testFile = tempDir.resolve("test_export.csv");
            Files.writeString(testFile, "test content");

            boolean exists = csvExportService.exportExists("test_export.csv");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when export file does not exist")
        void shouldReturnFalseWhenFileDoesNotExist() {
            boolean exists = csvExportService.exportExists("nonexistent.csv");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("getExportFilePath")
    class GetExportFilePathTests {

        @Test
        @DisplayName("should return correct file path")
        void shouldReturnCorrectFilePath() {
            Path path = csvExportService.getExportFilePath("test_file.csv");

            assertThat(path.toString()).endsWith("test_file.csv");
            assertThat(path.getParent().toString()).isEqualTo(tempDir.toString());
        }
    }
}
