CREATE TABLE hotel_expense_reversal (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 expense_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 initiated_by BIGINT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 approved_by BIGINT NULL,
 rejected_by BIGINT NULL,
 rejection_reason VARCHAR(500) NULL,
 posting_business_date DATE NULL,
 reversed_at DATETIME(6) NULL,
 external_reference VARCHAR(100) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_expense_reversal_request(request_key),
 KEY idx_expense_reversal_expense_status(expense_id,status,id),
 KEY idx_expense_reversal_posting(posting_business_date,status,id),
 CONSTRAINT fk_expense_reversal_expense FOREIGN KEY(expense_id) REFERENCES hotel_expense(id),
 CONSTRAINT fk_expense_reversal_initiator FOREIGN KEY(initiated_by) REFERENCES sys_user(id),
 CONSTRAINT fk_expense_reversal_approver FOREIGN KEY(approved_by) REFERENCES sys_user(id),
 CONSTRAINT fk_expense_reversal_rejector FOREIGN KEY(rejected_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_expense_reversal_no_delete BEFORE DELETE ON hotel_expense_reversal FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expense reversal history cannot be deleted'; END$$
CREATE TRIGGER trg_expense_reversal_completed_immutable BEFORE UPDATE ON hotel_expense_reversal FOR EACH ROW
BEGIN
 IF OLD.status IN(1,2) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolved expense reversal is immutable'; END IF;
 IF OLD.status=0 AND NEW.status NOT IN(1,2) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid expense reversal transition'; END IF;
END$$
DELIMITER ;
