CREATE TABLE gateway_settlement_reconciliation_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 attempt_no INT NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 status VARCHAR(16) NOT NULL,
 calculated_gross DECIMAL(12,2) NOT NULL,
 calculated_fee DECIMAL(12,2) NOT NULL,
 calculated_net DECIMAL(12,2) NOT NULL,
 exception_reason VARCHAR(500) NULL,
 performed_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_gateway_reconciliation_attempt(batch_id,attempt_no),
 UNIQUE KEY uk_gateway_reconciliation_request(batch_id,request_key),
 KEY idx_gateway_reconciliation_status(status,create_time,id),
 CONSTRAINT fk_gateway_reconciliation_batch FOREIGN KEY(batch_id) REFERENCES gateway_settlement_batch(id),
 CONSTRAINT fk_gateway_reconciliation_operator FOREIGN KEY(performed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gateway_settlement_reconciliation_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 attempt_id BIGINT NOT NULL,
 settlement_item_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 exception_reason VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_gateway_reconciliation_attempt_item(attempt_id,settlement_item_id),
 KEY idx_gateway_reconciliation_item_status(status,attempt_id),
 CONSTRAINT fk_gateway_reconciliation_item_attempt FOREIGN KEY(attempt_id) REFERENCES gateway_settlement_reconciliation_attempt(id),
 CONSTRAINT fk_gateway_reconciliation_item_source FOREIGN KEY(settlement_item_id) REFERENCES gateway_settlement_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO gateway_settlement_reconciliation_attempt(
 batch_id,attempt_no,request_key,status,calculated_gross,calculated_fee,calculated_net,exception_reason,performed_by,create_time
)
SELECT id,1,CONCAT('LEGACY:',id),reconciliation_status,calculated_gross,calculated_fee,calculated_net,exception_reason,imported_by,update_time
FROM gateway_settlement_batch;

INSERT INTO gateway_settlement_reconciliation_item(attempt_id,settlement_item_id,status,exception_reason,create_time)
SELECT a.id,i.id,i.status,i.exception_reason,i.create_time
FROM gateway_settlement_reconciliation_attempt a
JOIN gateway_settlement_item i ON i.batch_id=a.batch_id
WHERE a.attempt_no=1;

DELIMITER $$
CREATE TRIGGER trg_gateway_reconciliation_attempt_no_update BEFORE UPDATE ON gateway_settlement_reconciliation_attempt FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement reconciliation attempts are immutable'; END$$
CREATE TRIGGER trg_gateway_reconciliation_attempt_no_delete BEFORE DELETE ON gateway_settlement_reconciliation_attempt FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement reconciliation attempts are immutable'; END$$
CREATE TRIGGER trg_gateway_reconciliation_item_no_update BEFORE UPDATE ON gateway_settlement_reconciliation_item FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement reconciliation results are immutable'; END$$
CREATE TRIGGER trg_gateway_reconciliation_item_no_delete BEFORE DELETE ON gateway_settlement_reconciliation_item FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Settlement reconciliation results are immutable'; END$$
DELIMITER ;
