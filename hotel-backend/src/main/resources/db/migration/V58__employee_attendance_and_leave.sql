CREATE TABLE attendance_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    effective_check_in DATETIME(6) NOT NULL,
    effective_check_out DATETIME(6) NULL,
    check_in_punch_id BIGINT NULL,
    check_out_punch_id BIGINT NULL,
    open_employee_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 0 THEN employee_id ELSE NULL END) STORED,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_attendance_session_employee FOREIGN KEY (employee_id) REFERENCES sys_user(id),
    UNIQUE KEY uk_attendance_open_employee (open_employee_id),
    KEY idx_attendance_employee_time (employee_id, effective_check_in, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_punch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    punch_type TINYINT NOT NULL,
    punched_at DATETIME(6) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    source VARCHAR(32) NOT NULL,
    user_agent VARCHAR(500) NULL,
    operator_user_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_attendance_punch_employee FOREIGN KEY (employee_id) REFERENCES sys_user(id),
    CONSTRAINT fk_attendance_punch_session FOREIGN KEY (session_id) REFERENCES attendance_session(id),
    CONSTRAINT fk_attendance_punch_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
    UNIQUE KEY uk_attendance_punch_request (employee_id, request_key),
    KEY idx_attendance_punch_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE attendance_session
    ADD CONSTRAINT fk_attendance_session_in_punch FOREIGN KEY (check_in_punch_id) REFERENCES attendance_punch(id),
    ADD CONSTRAINT fk_attendance_session_out_punch FOREIGN KEY (check_out_punch_id) REFERENCES attendance_punch(id);

CREATE TABLE attendance_correction_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    department_id_snapshot BIGINT NOT NULL,
    approver_user_id_snapshot BIGINT NOT NULL,
    session_id BIGINT NULL,
    task_id BIGINT NULL,
    correction_type VARCHAR(32) NOT NULL,
    requested_check_in DATETIME(6) NULL,
    requested_check_out DATETIME(6) NULL,
    reason VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    request_key VARCHAR(100) NOT NULL,
    decision_note VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_by BIGINT NULL,
    decided_at DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_att_corr_employee FOREIGN KEY (employee_id) REFERENCES sys_user(id),
    CONSTRAINT fk_att_corr_department FOREIGN KEY (department_id_snapshot) REFERENCES department(id),
    CONSTRAINT fk_att_corr_approver FOREIGN KEY (approver_user_id_snapshot) REFERENCES sys_user(id),
    CONSTRAINT fk_att_corr_session FOREIGN KEY (session_id) REFERENCES attendance_session(id),
    CONSTRAINT fk_att_corr_task FOREIGN KEY (task_id) REFERENCES hotel_task(id),
    CONSTRAINT fk_att_corr_decider FOREIGN KEY (decided_by) REFERENCES sys_user(id),
    UNIQUE KEY uk_att_corr_request (employee_id, request_key),
    UNIQUE KEY uk_att_corr_task (task_id),
    KEY idx_att_corr_approver_status (approver_user_id_snapshot, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_correction_fact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    effective_check_in DATETIME(6) NOT NULL,
    effective_check_out DATETIME(6) NULL,
    approved_by BIGINT NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_att_corr_fact_request FOREIGN KEY (request_id) REFERENCES attendance_correction_request(id),
    CONSTRAINT fk_att_corr_fact_session FOREIGN KEY (session_id) REFERENCES attendance_session(id),
    CONSTRAINT fk_att_corr_fact_approver FOREIGN KEY (approved_by) REFERENCES sys_user(id),
    UNIQUE KEY uk_att_corr_fact_request (request_id),
    KEY idx_att_corr_fact_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    paid_treatment VARCHAR(16) NOT NULL,
    requires_balance TINYINT NOT NULL,
    requires_attachment TINYINT NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_leave_type_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_type_updater FOREIGN KEY (updated_by) REFERENCES sys_user(id),
    UNIQUE KEY uk_leave_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    created_by BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_leave_alloc_employee FOREIGN KEY (employee_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_alloc_type FOREIGN KEY (leave_type_id) REFERENCES leave_type(id),
    CONSTRAINT fk_leave_alloc_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    UNIQUE KEY uk_leave_alloc_request (created_by, request_key),
    KEY idx_leave_alloc_balance (employee_id, leave_type_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employee_leave_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    department_id_snapshot BIGINT NULL,
    approver_user_id_snapshot BIGINT NULL,
    leave_type_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    requested_amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    attachment_reference VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 0,
    task_id BIGINT NULL,
    request_key VARCHAR(100) NOT NULL,
    decision_note VARCHAR(500) NULL,
    submitted_at DATETIME(6) NULL,
    decided_by BIGINT NULL,
    decided_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    cancel_reason VARCHAR(500) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_leave_request_employee FOREIGN KEY (employee_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_request_department FOREIGN KEY (department_id_snapshot) REFERENCES department(id),
    CONSTRAINT fk_leave_request_approver FOREIGN KEY (approver_user_id_snapshot) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_request_type FOREIGN KEY (leave_type_id) REFERENCES leave_type(id),
    CONSTRAINT fk_leave_request_task FOREIGN KEY (task_id) REFERENCES hotel_task(id),
    CONSTRAINT fk_leave_request_decider FOREIGN KEY (decided_by) REFERENCES sys_user(id),
    UNIQUE KEY uk_leave_request_key (employee_id, request_key),
    UNIQUE KEY uk_leave_request_task (task_id),
    KEY idx_leave_employee_interval (employee_id, status, start_at, end_at),
    KEY idx_leave_approver_status (approver_user_id_snapshot, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_action_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    leave_request_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    detail VARCHAR(500) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_leave_history_request FOREIGN KEY (leave_request_id) REFERENCES employee_leave_request(id),
    CONSTRAINT fk_leave_history_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user(id),
    KEY idx_leave_history_request (leave_request_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TRIGGER trg_attendance_punch_no_update BEFORE UPDATE ON attendance_punch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='attendance punch is append-only';
CREATE TRIGGER trg_attendance_punch_no_delete BEFORE DELETE ON attendance_punch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='attendance punch is append-only';
CREATE TRIGGER trg_attendance_correction_fact_no_update BEFORE UPDATE ON attendance_correction_fact FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='attendance correction fact is append-only';
CREATE TRIGGER trg_attendance_correction_fact_no_delete BEFORE DELETE ON attendance_correction_fact FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='attendance correction fact is append-only';
CREATE TRIGGER trg_leave_allocation_no_update BEFORE UPDATE ON leave_allocation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='leave allocation is append-only';
CREATE TRIGGER trg_leave_allocation_no_delete BEFORE DELETE ON leave_allocation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='leave allocation is append-only';
