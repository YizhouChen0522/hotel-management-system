CREATE TABLE revenue_recognition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revenue_date DATE NOT NULL,
    recognition_business_date DATE NOT NULL,
    category VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    stay_id BIGINT NULL,
    folio_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_revenue_source (source_type,source_id),
    KEY idx_revenue_date (revenue_date,id),
    KEY idx_revenue_recognition_date (recognition_business_date,id),
    KEY idx_revenue_folio (folio_id,id),
    CONSTRAINT fk_revenue_stay FOREIGN KEY (stay_id) REFERENCES stay(id),
    CONSTRAINT fk_revenue_folio FOREIGN KEY (folio_id) REFERENCES folio(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_financial_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_date DATE NOT NULL,
    room_revenue DECIMAL(12,2) NOT NULL,
    other_revenue DECIMAL(12,2) NOT NULL,
    adjustments DECIMAL(12,2) NOT NULL,
    total_recognized_revenue DECIMAL(12,2) NOT NULL,
    external_cash_inflow DECIMAL(12,2) NOT NULL,
    external_cash_outflow DECIMAL(12,2) NOT NULL,
    night_audit_run_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_financial_summary_date (business_date),
    UNIQUE KEY uk_daily_financial_summary_run (night_audit_run_id),
    CONSTRAINT fk_daily_summary_run FOREIGN KEY (night_audit_run_id) REFERENCES night_audit_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_revenue_recognition_no_update BEFORE UPDATE ON revenue_recognition FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Revenue recognition is append-only'; END$$
CREATE TRIGGER trg_revenue_recognition_no_delete BEFORE DELETE ON revenue_recognition FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Revenue recognition is append-only'; END$$
CREATE TRIGGER trg_daily_financial_summary_no_update BEFORE UPDATE ON daily_financial_summary FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Daily financial summary is immutable'; END$$
CREATE TRIGGER trg_daily_financial_summary_no_delete BEFORE DELETE ON daily_financial_summary FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Daily financial summary is immutable'; END$$
DELIMITER ;
