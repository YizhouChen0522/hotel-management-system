CREATE TABLE night_audit_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_date DATE NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_step VARCHAR(40) NULL,
    started_by BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    last_error VARCHAR(500) NULL,
    processing_token VARCHAR(64) NULL,
    processing_until DATETIME(6) NULL,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_night_audit_business_date (business_date),
    UNIQUE KEY uk_night_audit_request_key (request_key),
    KEY idx_night_audit_status (status, business_date),
    CONSTRAINT fk_night_audit_started_by FOREIGN KEY (started_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE night_audit_blocker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    blocker_type VARCHAR(40) NOT NULL,
    object_type VARCHAR(40) NOT NULL,
    object_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    detected_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_night_audit_blocker (run_id, blocker_type, object_type, object_id),
    KEY idx_night_audit_blocker_active (run_id, active, id),
    CONSTRAINT fk_night_audit_blocker_run FOREIGN KEY (run_id) REFERENCES night_audit_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
