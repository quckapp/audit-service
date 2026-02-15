package com.quckapp.audit.domain.repository;

import com.quckapp.audit.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<AuditLog> findByWorkspaceIdAndActorIdOrderByCreatedAtDesc(UUID workspaceId, UUID actorId, Pageable pageable);

    Page<AuditLog> findByWorkspaceIdAndResourceTypeOrderByCreatedAtDesc(UUID workspaceId, String resourceType, Pageable pageable);

    Page<AuditLog> findByWorkspaceIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
        UUID workspaceId, String resourceType, UUID resourceId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceIdAndDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.action = :action ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceIdAndAction(
        @Param("workspaceId") UUID workspaceId,
        @Param("action") String action,
        Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.category = :category ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceIdAndCategory(
        @Param("workspaceId") UUID workspaceId,
        @Param("category") AuditLog.AuditCategory category,
        Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.severity IN :severities ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceIdAndSeverityIn(
        @Param("workspaceId") UUID workspaceId,
        @Param("severities") List<AuditLog.AuditSeverity> severities,
        Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end")
    long countByWorkspaceIdAndDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> countByActionInDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category")
    int deleteByCreatedAtBeforeAndCategory(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") Instant cutoffDate);

    // ===== Statistics Queries =====

    @Query("SELECT a.category, COUNT(a) FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end GROUP BY a.category")
    List<Object[]> countByCategoryInDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT a.severity, COUNT(a) FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end GROUP BY a.severity")
    List<Object[]> countBySeverityInDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT a.actorId, a.actorEmail, a.actorName, COUNT(a) FROM AuditLog a " +
           "WHERE a.workspaceId = :workspaceId AND a.createdAt BETWEEN :start AND :end " +
           "GROUP BY a.actorId, a.actorEmail, a.actorName ORDER BY COUNT(a) DESC")
    List<Object[]> findTopActorsInDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable);

    @Query("SELECT a.resourceType, a.resourceId, a.resourceName, COUNT(a) FROM AuditLog a " +
           "WHERE a.workspaceId = :workspaceId AND a.createdAt BETWEEN :start AND :end " +
           "GROUP BY a.resourceType, a.resourceId, a.resourceName ORDER BY COUNT(a) DESC")
    List<Object[]> findTopResourcesInDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable);

    // ===== Report Queries =====

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.category = :category AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByWorkspaceIdAndCategoryAndDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("category") AuditLog.AuditCategory category,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId " +
           "AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByWorkspaceIdAndDateRange(
        @Param("workspaceId") UUID workspaceId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    // ===== Retention Queries with Severity =====

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category " +
           "AND a.severity < :minSeverity")
    int deleteByCreatedAtBeforeAndCategoryAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.severity < :minSeverity")
    int deleteByCreatedAtBeforeAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);

    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category")
    List<UUID> findIdsByCreatedAtBeforeAndCategory(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category);

    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    List<UUID> findIdsByCreatedAtBefore(@Param("cutoffDate") Instant cutoffDate);

    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category " +
           "AND a.severity < :minSeverity")
    List<UUID> findIdsByCreatedAtBeforeAndCategoryAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);

    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.severity < :minSeverity")
    List<UUID> findIdsByCreatedAtBeforeAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);

    // ===== Archive Queries =====

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category")
    List<AuditLog> findByCreatedAtBeforeAndCategory(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    List<AuditLog> findByCreatedAtBefore(@Param("cutoffDate") Instant cutoffDate);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.category = :category " +
           "AND a.severity < :minSeverity")
    List<AuditLog> findByCreatedAtBeforeAndCategoryAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("category") AuditLog.AuditCategory category,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt < :cutoffDate AND a.severity < :minSeverity")
    List<AuditLog> findByCreatedAtBeforeAndSeverityLessThan(
        @Param("cutoffDate") Instant cutoffDate,
        @Param("minSeverity") AuditLog.AuditSeverity minSeverity);
}
