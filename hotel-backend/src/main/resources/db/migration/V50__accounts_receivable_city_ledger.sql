CREATE TABLE ar_account (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_type VARCHAR(30) NOT NULL,
 name VARCHAR(150) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
 currency VARCHAR(3) NOT NULL,
 credit_limit DECIMAL(12,2) NULL,
 payment_terms_days INT NULL,
 contact_name VARCHAR(120) NULL,
 contact_email VARCHAR(120) NULL,
 external_reference VARCHAR(100) NULL,
 created_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 KEY idx_ar_account_status_name(status,name,id),
 CONSTRAINT fk_ar_account_creator FOREIGN KEY(created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ar_ledger_entry (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 entry_type VARCHAR(24) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 folio_id BIGINT NULL,
 source_type VARCHAR(30) NOT NULL,
 source_id BIGINT NULL,
 request_key VARCHAR(100) NOT NULL,
 reason VARCHAR(500) NULL,
 payment_method VARCHAR(30) NULL,
 external_reference VARCHAR(100) NULL,
 operator_user_id BIGINT NOT NULL,
 posting_business_date DATE NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_ar_ledger_request(request_key),
 KEY idx_ar_ledger_account_date(account_id,posting_business_date,id),
 KEY idx_ar_ledger_folio_type(folio_id,entry_type,id),
 CONSTRAINT fk_ar_ledger_account FOREIGN KEY(account_id) REFERENCES ar_account(id),
 CONSTRAINT fk_ar_ledger_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
 CONSTRAINT fk_ar_ledger_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ar_request_lock (
 request_key VARCHAR(100) NOT NULL PRIMARY KEY,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE folio ADD COLUMN ar_transferred_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER refunded_amount;

DELIMITER $$
CREATE TRIGGER trg_ar_ledger_no_update BEFORE UPDATE ON ar_ledger_entry FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='AR ledger is append-only'; END$$
CREATE TRIGGER trg_ar_ledger_no_delete BEFORE DELETE ON ar_ledger_entry FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='AR ledger is append-only'; END$$
DELIMITER ;
