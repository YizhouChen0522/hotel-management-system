CREATE TABLE hotel_business_date_control (
    id TINYINT NOT NULL,
    business_date DATE NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    initialized_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO hotel_business_date_control(id,business_date,state,initialized_at,version)
VALUES(1,NULL,'OPEN',NULL,0);

CREATE TABLE hotel_business_date_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(30) NOT NULL,
    old_business_date DATE NULL,
    new_business_date DATE NOT NULL,
    state VARCHAR(20) NOT NULL,
    operator_user_id BIGINT NULL,
    occurred_at DATETIME(6) NOT NULL,
    initialization_key TINYINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_date_initialization (initialization_key),
    KEY idx_business_date_history_date (new_business_date,id),
    CONSTRAINT fk_business_date_history_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE deposit_payment ADD COLUMN business_date DATE NULL, ADD KEY idx_deposit_payment_business_date (business_date,id);
ALTER TABLE deposit_refund ADD COLUMN business_date DATE NULL, ADD KEY idx_deposit_refund_business_date (business_date,id);
ALTER TABLE deposit_transfer ADD COLUMN business_date DATE NULL, ADD KEY idx_deposit_transfer_business_date (business_date,id);
ALTER TABLE deposit_settlement ADD COLUMN business_date DATE NULL, ADD KEY idx_deposit_settlement_business_date (business_date,id);
ALTER TABLE folio_item ADD COLUMN posting_business_date DATE NULL, ADD KEY idx_folio_item_posting_business_date (posting_business_date,id);
ALTER TABLE payment ADD COLUMN business_date DATE NULL, ADD KEY idx_payment_business_date (business_date,id);
ALTER TABLE refund ADD COLUMN business_date DATE NULL, ADD KEY idx_refund_business_date (business_date,id);
ALTER TABLE hotel_transaction_record ADD COLUMN business_date DATE NULL, ADD KEY idx_hotel_transaction_business_date (business_date,id);
ALTER TABLE payment_attempt ADD COLUMN recognized_business_date DATE NULL, ADD KEY idx_payment_attempt_recognized_business_date (recognized_business_date,id);
