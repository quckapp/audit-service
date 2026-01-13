-- Audit logs table (partitioned by month for performance)
CREATE TABLE audit_logs (
    id BINARY(16) PRIMARY KEY,
    workspace_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_email VARCHAR(100),
    actor_name VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    resource_name VARCHAR(255),
    metadata JSON,
    previous_state JSON,
    new_state JSON,
    ip_address VARCHAR(50),
    user_agent VARCHAR(255),
    session_id VARCHAR(50),
    severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    category ENUM('AUTHENTICATION', 'AUTHORIZATION', 'DATA_ACCESS', 'DATA_MODIFICATION', 'CONFIGURATION', 'SECURITY', 'COMPLIANCE', 'SYSTEM') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_workspace (workspace_id),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_severity (severity),
    INDEX idx_audit_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Retention policies table
CREATE TABLE retention_policies (
    id BINARY(16) PRIMARY KEY,
    workspace_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    retention_days INT NOT NULL,
    category ENUM('AUTHENTICATION', 'AUTHORIZATION', 'DATA_ACCESS', 'DATA_MODIFICATION', 'CONFIGURATION', 'SECURITY', 'COMPLIANCE', 'SYSTEM'),
    min_severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    archive_before_delete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_retention_workspace (workspace_id),
    UNIQUE KEY uk_retention_workspace_name (workspace_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Compliance reports table
CREATE TABLE compliance_reports (
    id BINARY(16) PRIMARY KEY,
    workspace_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    report_type ENUM('ACCESS_LOG', 'LOGIN_HISTORY', 'DATA_EXPORT', 'SECURITY_AUDIT', 'USER_ACTIVITY', 'ADMIN_ACTIONS', 'COMPLIANCE_SUMMARY') NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    requested_by BINARY(16) NOT NULL,
    parameters JSON,
    summary JSON,
    file_url VARCHAR(500),
    file_size BIGINT,
    error_message VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    INDEX idx_report_workspace (workspace_id),
    INDEX idx_report_type (report_type),
    INDEX idx_report_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create default retention policies
INSERT INTO retention_policies (id, workspace_id, name, description, retention_days, category, min_severity, enabled, archive_before_delete)
VALUES
    (UUID_TO_BIN(UUID()), UUID_TO_BIN('00000000-0000-0000-0000-000000000000'), 'Default Security Retention', 'Keep security events for 2 years', 730, 'SECURITY', 'LOW', TRUE, TRUE),
    (UUID_TO_BIN(UUID()), UUID_TO_BIN('00000000-0000-0000-0000-000000000000'), 'Default Auth Retention', 'Keep authentication events for 1 year', 365, 'AUTHENTICATION', 'LOW', TRUE, TRUE),
    (UUID_TO_BIN(UUID()), UUID_TO_BIN('00000000-0000-0000-0000-000000000000'), 'Default Data Retention', 'Keep data access events for 90 days', 90, 'DATA_ACCESS', 'LOW', TRUE, FALSE);
