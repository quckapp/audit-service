package com.quikapp.audit.domain.repository;

import com.quikapp.audit.domain.document.AuditLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogElasticsearchRepository extends ElasticsearchRepository<AuditLogDocument, String> {

    Page<AuditLogDocument> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId, Pageable pageable);

    Page<AuditLogDocument> findByWorkspaceIdAndActorIdOrderByCreatedAtDesc(
        String workspaceId, String actorId, Pageable pageable);

    Page<AuditLogDocument> findByWorkspaceIdAndResourceTypeOrderByCreatedAtDesc(
        String workspaceId, String resourceType, Pageable pageable);

    Page<AuditLogDocument> findByWorkspaceIdAndActionOrderByCreatedAtDesc(
        String workspaceId, String action, Pageable pageable);

    Page<AuditLogDocument> findByWorkspaceIdAndCategoryOrderByCreatedAtDesc(
        String workspaceId, String category, Pageable pageable);

    Page<AuditLogDocument> findByWorkspaceIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        String workspaceId, Instant start, Instant end, Pageable pageable);

    @Query("{\"bool\": {\"must\": [{\"term\": {\"workspaceId\": \"?0\"}}, " +
           "{\"multi_match\": {\"query\": \"?1\", \"fields\": [\"actorEmail\", \"actorName\", \"resourceName\", \"action\"]}}]}}")
    Page<AuditLogDocument> searchByWorkspaceIdAndQuery(String workspaceId, String query, Pageable pageable);

    List<AuditLogDocument> findByWorkspaceIdAndSeverityInOrderByCreatedAtDesc(
        String workspaceId, List<String> severities);
}
