CREATE TABLE cash_drawer (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(50) NOT NULL,
 name VARCHAR(120) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
 active_shift_id BIGINT NULL,
 created_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_cash_drawer_code(code),
 UNIQUE KEY uk_cash_drawer_active_shift(active_shift_id),
 CONSTRAINT fk_cash_drawer_creator FOREIGN KEY(created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cashier_shift (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 drawer_id BIGINT NOT NULL,
 operator_user_id BIGINT NOT NULL,
 status VARCHAR(12) NOT NULL,
 opening_count DECIMAL(12,2) NOT NULL,
 expected_closing DECIMAL(12,2) NULL,
 closing_count DECIMAL(12,2) NULL,
 closing_variance DECIMAL(12,2) NULL,
 opened_at DATETIME(6) NOT NULL,
 closed_at DATETIME(6) NULL,
 opening_business_date DATE NOT NULL,
 closing_business_date DATE NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 KEY idx_cashier_shift_drawer(drawer_id,id),
 KEY idx_cashier_shift_operator(operator_user_id,status,id),
 CONSTRAINT fk_cashier_shift_drawer FOREIGN KEY(drawer_id) REFERENCES cash_drawer(id),
 CONSTRAINT fk_cashier_shift_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE cash_drawer ADD CONSTRAINT fk_cash_drawer_active_shift FOREIGN KEY(active_shift_id) REFERENCES cashier_shift(id);

CREATE TABLE cash_drawer_movement (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 drawer_id BIGINT NOT NULL,
 shift_id BIGINT NOT NULL,
 direction VARCHAR(3) NOT NULL,
 movement_type VARCHAR(32) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 source_type VARCHAR(32) NOT NULL,
 source_id BIGINT NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 operator_user_id BIGINT NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 posting_business_date DATE NOT NULL,
 note VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_cash_movement_source(source_type,source_id),
 UNIQUE KEY uk_cash_movement_request(request_key),
 KEY idx_cash_movement_shift(shift_id,id),
 KEY idx_cash_movement_drawer_date(drawer_id,posting_business_date,id),
 CONSTRAINT fk_cash_movement_drawer FOREIGN KEY(drawer_id) REFERENCES cash_drawer(id),
 CONSTRAINT fk_cash_movement_shift FOREIGN KEY(shift_id) REFERENCES cashier_shift(id),
 CONSTRAINT fk_cash_movement_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cash_variance (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, shift_id BIGINT NOT NULL,
 expected_amount DECIMAL(12,2) NOT NULL,counted_amount DECIMAL(12,2) NOT NULL,difference_amount DECIMAL(12,2) NOT NULL,
 variance_type VARCHAR(8) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'OPEN',reason VARCHAR(500) NOT NULL,
 recorded_by BIGINT NOT NULL,reviewed_by BIGINT NULL,reviewed_at DATETIME(6) NULL,resolution_note VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_cash_variance_shift(shift_id),CONSTRAINT fk_cash_variance_shift FOREIGN KEY(shift_id) REFERENCES cashier_shift(id),
 CONSTRAINT fk_cash_variance_recorder FOREIGN KEY(recorded_by) REFERENCES sys_user(id),CONSTRAINT fk_cash_variance_reviewer FOREIGN KEY(reviewed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cash_handover (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,drawer_id BIGINT NOT NULL,previous_shift_id BIGINT NOT NULL,next_shift_id BIGINT NOT NULL,
 previous_closing_count DECIMAL(12,2) NOT NULL,next_opening_count DECIMAL(12,2) NOT NULL,difference_amount DECIMAL(12,2) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'OPEN',recorded_by BIGINT NOT NULL,create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_cash_handover_next(next_shift_id),CONSTRAINT fk_cash_handover_drawer FOREIGN KEY(drawer_id) REFERENCES cash_drawer(id),
 CONSTRAINT fk_cash_handover_previous FOREIGN KEY(previous_shift_id) REFERENCES cashier_shift(id),CONSTRAINT fk_cash_handover_next FOREIGN KEY(next_shift_id) REFERENCES cashier_shift(id),
 CONSTRAINT fk_cash_handover_recorder FOREIGN KEY(recorded_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cash_transfer_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,drawer_id BIGINT NOT NULL,shift_id BIGINT NOT NULL,task_id BIGINT NOT NULL,
 request_type VARCHAR(24) NOT NULL,amount DECIMAL(12,2) NOT NULL,currency VARCHAR(3) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 request_key VARCHAR(100) NOT NULL,requested_by BIGINT NOT NULL,confirmed_by BIGINT NULL,confirmed_at DATETIME(6) NULL,note VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),UNIQUE KEY uk_cash_transfer_request(request_key),UNIQUE KEY uk_cash_transfer_task(task_id),
 CONSTRAINT fk_cash_transfer_drawer FOREIGN KEY(drawer_id) REFERENCES cash_drawer(id),CONSTRAINT fk_cash_transfer_shift FOREIGN KEY(shift_id) REFERENCES cashier_shift(id),
 CONSTRAINT fk_cash_transfer_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),CONSTRAINT fk_cash_transfer_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_cash_transfer_confirmer FOREIGN KEY(confirmed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_cash_movement_no_update BEFORE UPDATE ON cash_drawer_movement FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cash movements are append-only'; END$$
CREATE TRIGGER trg_cash_movement_no_delete BEFORE DELETE ON cash_drawer_movement FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cash movements are append-only'; END$$
CREATE TRIGGER trg_cash_shift_closed_immutable BEFORE UPDATE ON cashier_shift FOR EACH ROW BEGIN IF OLD.status='CLOSED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Closed cashier shift is immutable'; END IF; END$$
DELIMITER ;
