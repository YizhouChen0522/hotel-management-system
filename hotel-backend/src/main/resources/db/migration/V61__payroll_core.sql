CREATE TABLE employee_compensation_profile (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, employee_id BIGINT NOT NULL, pay_type VARCHAR(16) NOT NULL,
 currency VARCHAR(3) NOT NULL, base_amount DECIMAL(12,2) NULL, hourly_rate DECIMAL(12,2) NULL,
 effective_from DATE NOT NULL, effective_to DATE NULL, status TINYINT NOT NULL DEFAULT 1,
 reason VARCHAR(500) NOT NULL, created_by BIGINT NOT NULL, approved_by BIGINT NOT NULL, approved_at DATETIME(6) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_comp_employee FOREIGN KEY(employee_id) REFERENCES sys_user(id),
 CONSTRAINT fk_comp_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_comp_approver FOREIGN KEY(approved_by) REFERENCES sys_user(id),
 KEY idx_comp_employee_period(employee_id,status,effective_from,effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_policy (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, version_no INT NOT NULL, status VARCHAR(12) NOT NULL,
 monthly_unpaid_mode VARCHAR(24) NULL, hourly_paid_leave_mode VARCHAR(24) NULL,
 standard_days DECIMAL(8,2) NULL, standard_hours_per_day DECIMAL(8,2) NULL,
 rounding_mode VARCHAR(20) NOT NULL, created_by BIGINT NOT NULL, activated_by BIGINT NOT NULL,
 activated_at DATETIME(6) NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 active_key TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uk_payroll_policy_version(version_no), UNIQUE KEY uk_payroll_policy_active(active_key),
 CONSTRAINT fk_payroll_policy_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_payroll_policy_activator FOREIGN KEY(activated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_period (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, period_start DATETIME(6) NOT NULL, period_end DATETIME(6) NOT NULL,
 currency VARCHAR(3) NOT NULL, status TINYINT NOT NULL DEFAULT 0, policy_id BIGINT NULL,
 created_by BIGINT NOT NULL, approved_by BIGINT NULL, approved_at DATETIME(6) NULL,
 paid_at DATETIME(6) NULL, closed_at DATETIME(6) NULL, request_key VARCHAR(100) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_payroll_period_request(created_by,request_key), KEY idx_payroll_period_range(status,period_start,period_end),
 CONSTRAINT fk_payroll_period_policy FOREIGN KEY(policy_id) REFERENCES payroll_policy(id),
 CONSTRAINT fk_payroll_period_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_payroll_period_approver FOREIGN KEY(approved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_payslip (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payroll_period_id BIGINT NOT NULL, employee_id BIGINT NOT NULL,
 compensation_profile_id BIGINT NULL, status TINYINT NOT NULL, blocked_reason VARCHAR(100) NULL,
 pay_type VARCHAR(16) NULL, currency VARCHAR(3) NOT NULL, rate_snapshot DECIMAL(12,2) NULL,
 worked_minutes BIGINT NOT NULL DEFAULT 0, attendance_session_count INT NOT NULL DEFAULT 0,
 attendance_anomaly_count INT NOT NULL DEFAULT 0, correction_fact_count INT NOT NULL DEFAULT 0,
 paid_leave_amount DECIMAL(10,2) NOT NULL DEFAULT 0, unpaid_leave_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
 leave_snapshot TEXT NULL, policy_snapshot VARCHAR(500) NULL,
 base_pay DECIMAL(12,2) NOT NULL DEFAULT 0, attendance_based_pay DECIMAL(12,2) NOT NULL DEFAULT 0,
 paid_leave_pay DECIMAL(12,2) NOT NULL DEFAULT 0, unpaid_leave_deduction DECIMAL(12,2) NOT NULL DEFAULT 0,
 allowances DECIMAL(12,2) NOT NULL DEFAULT 0, bonuses DECIMAL(12,2) NOT NULL DEFAULT 0,
 manual_deductions DECIMAL(12,2) NOT NULL DEFAULT 0, other_adjustments DECIMAL(12,2) NOT NULL DEFAULT 0,
 gross_pay DECIMAL(12,2) NOT NULL DEFAULT 0, total_deductions DECIMAL(12,2) NOT NULL DEFAULT 0,
 net_pay DECIMAL(12,2) NOT NULL DEFAULT 0, calculated_at DATETIME(6) NOT NULL,
 approved_at DATETIME(6) NULL, paid_at DATETIME(6) NULL, expense_id BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_payslip_period_employee(payroll_period_id,employee_id), UNIQUE KEY uk_payslip_expense(expense_id),
 KEY idx_payslip_employee(employee_id,status,id),
 CONSTRAINT fk_payslip_period FOREIGN KEY(payroll_period_id) REFERENCES payroll_period(id),
 CONSTRAINT fk_payslip_employee FOREIGN KEY(employee_id) REFERENCES sys_user(id),
 CONSTRAINT fk_payslip_comp FOREIGN KEY(compensation_profile_id) REFERENCES employee_compensation_profile(id),
 CONSTRAINT fk_payslip_expense FOREIGN KEY(expense_id) REFERENCES hotel_expense(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_period_employee (
 payroll_period_id BIGINT NOT NULL, employee_id BIGINT NOT NULL,
 PRIMARY KEY(payroll_period_id,employee_id),
 CONSTRAINT fk_payroll_member_period FOREIGN KEY(payroll_period_id) REFERENCES payroll_period(id),
 CONSTRAINT fk_payroll_member_employee FOREIGN KEY(employee_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_adjustment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payslip_id BIGINT NOT NULL, adjustment_type VARCHAR(20) NOT NULL,
 direction VARCHAR(10) NOT NULL, amount DECIMAL(12,2) NOT NULL, reason VARCHAR(500) NOT NULL,
 created_by BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_payroll_adjustment_request(payslip_id,request_key),
 CONSTRAINT fk_payroll_adjustment_payslip FOREIGN KEY(payslip_id) REFERENCES payroll_payslip(id),
 CONSTRAINT fk_payroll_adjustment_creator FOREIGN KEY(created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_payment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payslip_id BIGINT NOT NULL, amount DECIMAL(12,2) NOT NULL,
 payment_method VARCHAR(30) NOT NULL, external_reference VARCHAR(100) NULL, request_key VARCHAR(100) NOT NULL,
 paid_by BIGINT NOT NULL, posting_business_date DATE NOT NULL, paid_at DATETIME(6) NOT NULL,
 expense_id BIGINT NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_payroll_payment_payslip(payslip_id), UNIQUE KEY uk_payroll_payment_request(request_key), UNIQUE KEY uk_payroll_payment_expense(expense_id),
 CONSTRAINT fk_payroll_payment_payslip FOREIGN KEY(payslip_id) REFERENCES payroll_payslip(id),
 CONSTRAINT fk_payroll_payment_actor FOREIGN KEY(paid_by) REFERENCES sys_user(id),
 CONSTRAINT fk_payroll_payment_expense FOREIGN KEY(expense_id) REFERENCES hotel_expense(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payroll_action_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, period_id BIGINT NULL, payslip_id BIGINT NULL,
 action_type VARCHAR(40) NOT NULL, actor_user_id BIGINT NOT NULL, detail VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_payroll_action_period(period_id,id), KEY idx_payroll_action_payslip(payslip_id,id),
 CONSTRAINT fk_payroll_action_period FOREIGN KEY(period_id) REFERENCES payroll_period(id),
 CONSTRAINT fk_payroll_action_payslip FOREIGN KEY(payslip_id) REFERENCES payroll_payslip(id),
 CONSTRAINT fk_payroll_action_actor FOREIGN KEY(actor_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
CREATE TRIGGER trg_paid_payslip_immutable BEFORE UPDATE ON payroll_payslip FOR EACH ROW
BEGIN IF OLD.status=3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Paid payslip is immutable'; END IF; END$$
CREATE TRIGGER trg_payroll_payment_no_update BEFORE UPDATE ON payroll_payment FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Payroll payment is append-only'; END$$
CREATE TRIGGER trg_payroll_payment_no_delete BEFORE DELETE ON payroll_payment FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Payroll payment is append-only'; END$$
DELIMITER ;
