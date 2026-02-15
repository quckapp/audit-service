package com.quckapp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quckapp.audit.domain.document.AuditLogDocument;
import com.quckapp.audit.domain.entity.AuditLog;
import com.quckapp.audit.domain.repository.AuditLogElasticsearchRepository;
import com.quckapp.audit.domain.repository.AuditLogRepository;
import com.quckapp.audit.dto.AuditDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quckapp.audit.exception.ResourceNotFoundException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogElasticsearchRepository elasticsearchRepository;
    private final ObjectMapper objectMapper;

    public AuditLogResponse createAuditLog(CreateAuditLogRequest request) {
        AuditLog auditLog = AuditLog.builder()
            .workspaceId(request.getWorkspaceId())
            .actorId(request.getActorId())
            .actorEmail(request.getActorEmail())
            .actorName(request.getActorName())
            .action(request.getAction())
            .resourceType(request.getResourceType())
            .resourceId(request.getResourceId())
            .resourceName(request.getResourceName())
            .metadata(toJson(request.getMetadata()))
            .previousState(request.getPreviousState())
            .newState(request.getNewState())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .sessionId(request.getSessionId())
            .severity(request.getSeverity())
            .category(request.getCategory())
            .build();

        auditLog = auditLogRepository.save(auditLog);
        log.debug("Created audit log: {} - {} on {}", request.getAction(), request.getResourceType(), request.getResourceId());

        // Index to Elasticsearch asynchronously
        indexToElasticsearchAsync(auditLog);

        return mapToResponse(auditLog);
    }

    @Async
    protected void indexToElasticsearchAsync(AuditLog auditLog) {
        try {
            AuditLogDocument document = AuditLogDocument.fromEntity(auditLog);
            elasticsearchRepository.save(document);
            log.debug("Indexed audit log to Elasticsearch: {}", auditLog.getId());
        } catch (Exception e) {
            log.error("Failed to index audit log to Elasticsearch: {}", auditLog.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> searchAuditLogs(AuditLogSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<AuditLog> page;

        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            // Use Elasticsearch for full-text search
            Page<AuditLogDocument> esPage = elasticsearchRepository.searchByWorkspaceIdAndQuery(
                request.getWorkspaceId().toString(), request.getQuery(), pageable);
            return mapEsPageToResponse(esPage);
        }

        // Use MySQL for structured queries
        if (request.getActorId() != null) {
            page = auditLogRepository.findByWorkspaceIdAndActorIdOrderByCreatedAtDesc(
                request.getWorkspaceId(), request.getActorId(), pageable);
        } else if (request.getResourceType() != null && request.getResourceId() != null) {
            page = auditLogRepository.findByWorkspaceIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
                request.getWorkspaceId(), request.getResourceType(), request.getResourceId(), pageable);
        } else if (request.getStartDate() != null && request.getEndDate() != null) {
            page = auditLogRepository.findByWorkspaceIdAndDateRange(
                request.getWorkspaceId(), request.getStartDate(), request.getEndDate(), pageable);
        } else if (request.getCategory() != null) {
            page = auditLogRepository.findByWorkspaceIdAndCategory(
                request.getWorkspaceId(), request.getCategory(), pageable);
        } else if (request.getSeverities() != null && !request.getSeverities().isEmpty()) {
            page = auditLogRepository.findByWorkspaceIdAndSeverityIn(
                request.getWorkspaceId(), new ArrayList<>(request.getSeverities()), pageable);
        } else {
            page = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(request.getWorkspaceId(), pageable);
        }

        return mapPageToResponse(page);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(UUID id) {
        AuditLog auditLog = auditLogRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Audit log not found"));
        return mapToResponse(auditLog);
    }

    @Transactional(readOnly = true)
    public AuditStatistics getStatistics(UUID workspaceId, Instant startDate, Instant endDate) {
        if (startDate == null) startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        if (endDate == null) endDate = Instant.now();

        long totalEvents = auditLogRepository.countByWorkspaceIdAndDateRange(workspaceId, startDate, endDate);
        List<Object[]> actionCounts = auditLogRepository.countByActionInDateRange(workspaceId, startDate, endDate);
        List<Object[]> categoryCounts = auditLogRepository.countByCategoryInDateRange(workspaceId, startDate, endDate);
        List<Object[]> severityCounts = auditLogRepository.countBySeverityInDateRange(workspaceId, startDate, endDate);
        List<Object[]> topActors = auditLogRepository.findTopActorsInDateRange(workspaceId, startDate, endDate, PageRequest.of(0, 10));
        List<Object[]> topResources = auditLogRepository.findTopResourcesInDateRange(workspaceId, startDate, endDate, PageRequest.of(0, 10));

        Map<String, Long> eventsByAction = actionCounts.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));

        Map<String, Long> eventsByCategory = categoryCounts.stream()
            .collect(Collectors.toMap(
                row -> ((AuditLog.AuditCategory) row[0]).name(),
                row -> (Long) row[1]
            ));

        Map<String, Long> eventsBySeverity = severityCounts.stream()
            .collect(Collectors.toMap(
                row -> ((AuditLog.AuditSeverity) row[0]).name(),
                row -> (Long) row[1]
            ));

        List<TopActor> topActorList = topActors.stream()
            .map(row -> TopActor.builder()
                .actorId((UUID) row[0])
                .actorEmail((String) row[1])
                .actorName((String) row[2])
                .eventCount((Long) row[3])
                .build())
            .toList();

        List<TopResource> topResourceList = topResources.stream()
            .map(row -> TopResource.builder()
                .resourceType((String) row[0])
                .resourceId((UUID) row[1])
                .resourceName((String) row[2])
                .eventCount((Long) row[3])
                .build())
            .toList();

        return AuditStatistics.builder()
            .workspaceId(workspaceId)
            .periodStart(startDate)
            .periodEnd(endDate)
            .totalEvents(totalEvents)
            .eventsByAction(eventsByAction)
            .eventsByCategory(eventsByCategory)
            .eventsBySeverity(eventsBySeverity)
            .topActors(topActorList)
            .topResources(topResourceList)
            .build();
    }

    private PagedResponse<AuditLogResponse> mapPageToResponse(Page<AuditLog> page) {
        return PagedResponse.<AuditLogResponse>builder()
            .content(page.getContent().stream().map(this::mapToResponse).toList())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .build();
    }

    private PagedResponse<AuditLogResponse> mapEsPageToResponse(Page<AuditLogDocument> page) {
        return PagedResponse.<AuditLogResponse>builder()
            .content(page.getContent().stream().map(this::mapDocumentToResponse).toList())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .build();
    }

    private AuditLogResponse mapToResponse(AuditLog entity) {
        return AuditLogResponse.builder()
            .id(entity.getId())
            .workspaceId(entity.getWorkspaceId())
            .actorId(entity.getActorId())
            .actorEmail(entity.getActorEmail())
            .actorName(entity.getActorName())
            .action(entity.getAction())
            .resourceType(entity.getResourceType())
            .resourceId(entity.getResourceId())
            .resourceName(entity.getResourceName())
            .metadata(fromJson(entity.getMetadata()))
            .previousState(entity.getPreviousState())
            .newState(entity.getNewState())
            .ipAddress(entity.getIpAddress())
            .userAgent(entity.getUserAgent())
            .severity(entity.getSeverity())
            .category(entity.getCategory())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private AuditLogResponse mapDocumentToResponse(AuditLogDocument doc) {
        return AuditLogResponse.builder()
            .id(UUID.fromString(doc.getId()))
            .workspaceId(UUID.fromString(doc.getWorkspaceId()))
            .actorId(UUID.fromString(doc.getActorId()))
            .actorEmail(doc.getActorEmail())
            .actorName(doc.getActorName())
            .action(doc.getAction())
            .resourceType(doc.getResourceType())
            .resourceId(UUID.fromString(doc.getResourceId()))
            .resourceName(doc.getResourceName())
            .metadata(doc.getMetadata())
            .ipAddress(doc.getIpAddress())
            .userAgent(doc.getUserAgent())
            .severity(AuditLog.AuditSeverity.valueOf(doc.getSeverity()))
            .category(AuditLog.AuditCategory.valueOf(doc.getCategory()))
            .createdAt(doc.getCreatedAt())
            .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize from JSON", e);
            return null;
        }
    }
}
