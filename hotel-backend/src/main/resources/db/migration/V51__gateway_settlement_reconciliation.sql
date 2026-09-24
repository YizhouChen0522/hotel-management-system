CREATE TABLE gateway_settlement_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 provider VARCHAR(30) NOT NULL,
 provider_batch_id VARCHAR(100) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 period_start DATETIME(6) NULL,
 period_end DATETIME(6) NULL,
 declared_gross DECIMAL(12,2) NOT NULL,
 declared_fee DECIMAL(12,2) NOT NULL,
 declared_net DECIMAL(12,2) NOT NULL,
 calculated_gross DECIMAL(12,2) NOT NULL DEFAULT 0,
 calculated_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
 calculated_net DECIMAL(12,2) NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL,
 reconciliation_status VARCHAR(16) NOT NULL,
 exception_reason VARCHAR(500) NULL,
 posting_business_date DATE NOT NULL,
 provider_created_at DATETIME(6) NULL,
 provider_settled_at DATETIME(6) NULL,
 imported_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_gateway_batch_provider(provider,provider_batch_id),
 KEY idx_gateway_batch_date(posting_business_date,status,id),
 CONSTRAINT fk_gateway_batch_importer FOREIGN KEY(imported_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gateway_settlement_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 source_type VARCHAR(30) NOT NULL,
 source_id BIGINT NOT NULL,
 provider_payment_id VARCHAR(100) NOT NULL,
 direction VARCHAR(16) NOT NULL,
 gross_amount DECIMAL(12,2) NOT NULL,
 fee_amount DECIMAL(12,2) NOT NULL,
 net_amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 status VARCHAR(16) NOT NULL,
 exception_reason VARCHAR(500) NULL,
 provider_occurred_at DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_gateway_item_source(source_type,source_id),
 UNIQUE KEY uk_gateway_item_provider(provider_payment_id,direction),
 KEY idx_gateway_item_batch(batch_id,id),
 CONSTRAINT fk_gateway_item_batch FOREIGN KEY(batch_id) REFERENCES gateway_settlement_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gateway_payout (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 provider VARCHAR(30) NOT NULL,
 provider_payout_id VARCHAR(100) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 status VARCHAR(16) NOT NULL,
 reconciliation_status VARCHAR(16) NOT NULL,
 exception_reason VARCHAR(500) NULL,
 expected_at DATETIME(6) NULL,
 paid_at DATETIME(6) NULL,
 bank_reference VARCHAR(100) NULL,
 posting_business_date DATE NOT NULL,
 confirmed_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_gateway_payout_provider(provider,provider_payout_id),
 UNIQUE KEY uk_gateway_payout_batch(batch_id),
 KEY idx_gateway_payout_date(posting_business_date,status,id),
 CONSTRAINT fk_gateway_payout_batch FOREIGN KEY(batch_id) REFERENCES gateway_settlement_batch(id),
 CONSTRAINT fk_gateway_payout_confirmer FOREIGN KEY(confirmed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_gateway_item_no_update BEFORE UPDATE ON gateway_settlement_item FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement items are immutable'; END$$
CREATE TRIGGER trg_gateway_item_no_delete BEFORE DELETE ON gateway_settlement_item FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement items are immutable'; END$$
CREATE TRIGGER trg_gateway_payout_completed_immutable BEFORE UPDATE ON gateway_payout FOR EACH ROW BEGIN IF OLD.status='COMPLETED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Completed payout is immutable'; END IF; END$$
DELIMITER ;
