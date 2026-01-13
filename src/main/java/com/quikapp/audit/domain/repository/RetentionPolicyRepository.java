package com.quikapp.audit.domain.repository;

import com.quikapp.audit.domain.entity.AuditLog;
import com.quikapp.audit.domain.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    List<RetentionPolicy> findByWorkspaceId(UUID workspaceId);

    List<RetentionPolicy> findByEnabledTrue();

    Optional<RetentionPolicy> findByWorkspaceIdAndCategory(UUID workspaceId, AuditLog.AuditCategory category);

    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
