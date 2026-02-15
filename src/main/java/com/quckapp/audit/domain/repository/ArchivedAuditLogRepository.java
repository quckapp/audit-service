package com.quckapp.audit.domain.repository;

import com.quckapp.audit.domain.entity.ArchivedAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ArchivedAuditLogRepository extends JpaRepository<ArchivedAuditLog, UUID> {

    Page<ArchivedAuditLog> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<ArchivedAuditLog> findByArchivedByPolicyIdOrderByArchivedAtDesc(UUID policyId, Pageable pageable);

    @Query("SELECT a FROM ArchivedAuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<ArchivedAuditLog> findByWorkspaceIdAndDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable);

    @Query("SELECT COUNT(a) FROM ArchivedAuditLog a WHERE a.archivedByPolicyId = :policyId")
    long countByPolicyId(@Param("policyId") UUID policyId);

    List<ArchivedAuditLog> findByArchivedByPolicyId(UUID policyId);
}
