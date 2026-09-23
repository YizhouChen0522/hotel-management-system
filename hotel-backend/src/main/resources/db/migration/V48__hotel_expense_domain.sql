CREATE TABLE hotel_expense (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 category VARCHAR(24) NOT NULL,
 description VARCHAR(500) NOT NULL,
 reference_no VARCHAR(100) NULL,
 expense_date DATE NOT NULL,
 service_date DATE NULL,
 posting_business_date DATE NULL,
 status TINYINT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL,
 approved_by BIGINT NULL,
 approved_at DATETIME(6) NULL,
 rejected_by BIGINT NULL,
 rejected_at DATETIME(6) NULL,
 rejection_reason VARCHAR(500) NULL,
 paid_by BIGINT NULL,
 paid_at DATETIME(6) NULL,
 payment_method VARCHAR(30) NULL,
 external_reference VARCHAR(100) NULL,
 related_type VARCHAR(30) NULL,
 related_id BIGINT NULL,
 employee_user_id BIGINT NULL,
 request_key VARCHAR(100) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_hotel_expense_request(created_by,request_key),
 KEY idx_hotel_expense_posting(posting_business_date,status,id),
 KEY idx_hotel_expense_date_category(expense_date,category,id),
 KEY idx_hotel_expense_related(related_type,related_id),
 KEY idx_hotel_expense_employee(employee_user_id,id),
 CONSTRAINT fk_hotel_expense_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_hotel_expense_approver FOREIGN KEY(approved_by) REFERENCES sys_user(id),
 CONSTRAINT fk_hotel_expense_rejector FOREIGN KEY(rejected_by) REFERENCES sys_user(id),
 CONSTRAINT fk_hotel_expense_payer FOREIGN KEY(paid_by) REFERENCES sys_user(id),
 CONSTRAINT fk_hotel_expense_employee FOREIGN KEY(employee_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hotel_expense_action (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 expense_id BIGINT NOT NULL,
 action_type VARCHAR(30) NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 operator_user_id BIGINT NOT NULL,
 reason VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_hotel_expense_action(expense_id,action_type,request_key),
 KEY idx_hotel_expense_action_expense(expense_id,id),
 CONSTRAINT fk_hotel_expense_action_expense FOREIGN KEY(expense_id) REFERENCES hotel_expense(id),
 CONSTRAINT fk_hotel_expense_action_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_hotel_expense_no_delete BEFORE DELETE ON hotel_expense FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hotel expense history cannot be deleted'; END$$
CREATE TRIGGER trg_hotel_expense_paid_immutable BEFORE UPDATE ON hotel_expense FOR EACH ROW
BEGIN
 IF OLD.status=3 AND (NOT(OLD.amount<=>NEW.amount) OR NOT(OLD.currency<=>NEW.currency) OR NOT(OLD.category<=>NEW.category)
    OR NOT(OLD.expense_date<=>NEW.expense_date) OR NOT(OLD.service_date<=>NEW.service_date)
    OR NOT(OLD.posting_business_date<=>NEW.posting_business_date) OR NOT(OLD.paid_at<=>NEW.paid_at)
    OR NOT(OLD.payment_method<=>NEW.payment_method) OR NOT(OLD.external_reference<=>NEW.external_reference)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Paid expense financial facts are immutable';
 END IF;
END$$
CREATE TRIGGER trg_hotel_expense_action_no_update BEFORE UPDATE ON hotel_expense_action FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expense action history is append-only'; END$$
CREATE TRIGGER trg_hotel_expense_action_no_delete BEFORE DELETE ON hotel_expense_action FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expense action history is append-only'; END$$
DELIMITER ;
