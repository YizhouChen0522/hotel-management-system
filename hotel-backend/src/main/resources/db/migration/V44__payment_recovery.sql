ALTER TABLE payment_attempt
 ADD COLUMN recovery_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' AFTER fulfillment_status,
 ADD COLUMN recovery_retry_count INT NOT NULL DEFAULT 0 AFTER failure_message,
 ADD COLUMN next_recovery_at DATETIME(6) NULL AFTER recovery_retry_count,
 ADD COLUMN last_recovery_at DATETIME(6) NULL AFTER next_recovery_at,
 ADD COLUMN last_recovery_error VARCHAR(255) NULL AFTER last_recovery_at,
 ADD COLUMN recovery_claim_token VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER last_recovery_error,
 ADD COLUMN recovery_claim_until DATETIME(6) NULL AFTER recovery_claim_token,
 ADD KEY idx_attempt_recovery_due(recovery_status,status,fulfillment_status,next_recovery_at,updated_at),
 ADD KEY idx_attempt_recovery_lease(recovery_claim_until);
