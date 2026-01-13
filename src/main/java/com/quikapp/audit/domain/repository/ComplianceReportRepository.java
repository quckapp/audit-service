package com.quikapp.audit.domain.repository;

import com.quikapp.audit.domain.entity.ComplianceReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, UUID> {

    Page<ComplianceReport> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    List<ComplianceReport> findByWorkspaceIdAndReportTypeOrderByCreatedAtDesc(
        UUID workspaceId, ComplianceReport.ReportType reportType);

    List<ComplianceReport> findByStatus(ComplianceReport.ReportStatus status);

    List<ComplianceReport> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(
        UUID workspaceId, ComplianceReport.ReportStatus status);
}
