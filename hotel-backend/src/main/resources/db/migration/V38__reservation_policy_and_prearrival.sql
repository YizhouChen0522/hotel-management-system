CREATE TABLE reservation_policy (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 version_no BIGINT NOT NULL,
 name VARCHAR(120) NOT NULL,
 status TINYINT NOT NULL DEFAULT 0 COMMENT '0 DRAFT, 1 ACTIVE, 2 DISABLED',
 active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status=1 THEN 1 ELSE NULL END) STORED,
 created_by BIGINT NOT NULL,
 activated_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 activated_time DATETIME(6) NULL,
 UNIQUE KEY uk_reservation_policy_version(version_no),
 UNIQUE KEY uk_reservation_policy_active(active_slot),
 CONSTRAINT fk_reservation_policy_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_reservation_policy_activator FOREIGN KEY(activated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_policy_band (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 policy_id BIGINT NOT NULL,
 min_lead_days INT NOT NULL,
 max_lead_days INT NULL,
 refund_percent DECIMAL(5,2) NOT NULL,
 UNIQUE KEY uk_reservation_policy_band_start(policy_id,min_lead_days),
 CONSTRAINT fk_reservation_policy_band_policy FOREIGN KEY(policy_id) REFERENCES reservation_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_policy_lock (
 id TINYINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO reservation_policy_lock(id) VALUES(1);

ALTER TABLE booking
 ADD COLUMN reservation_policy_id BIGINT NULL,
 ADD COLUMN staff_direct_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD UNIQUE KEY uk_booking_staff_direct_request(staff_direct_request_key),
 ADD KEY idx_booking_reservation_policy(reservation_policy_id),
 ADD CONSTRAINT fk_booking_reservation_policy FOREIGN KEY(reservation_policy_id) REFERENCES reservation_policy(id);

CREATE TABLE staff_direct_request_lock (
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_cancellation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL,
 initiator VARCHAR(12) NOT NULL,
 operator_user_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 policy_id BIGINT NULL,
 lead_days INT NULL,
 refund_percent DECIMAL(5,2) NULL,
 deposit_before DECIMAL(12,2) NOT NULL,
 refund_obligation DECIMAL(12,2) NOT NULL,
 forfeited_amount DECIMAL(12,2) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_reservation_cancellation_booking(booking_id),
 CONSTRAINT fk_reservation_cancellation_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_reservation_cancellation_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_reservation_cancellation_policy FOREIGN KEY(policy_id) REFERENCES reservation_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_no_show (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL,
 operator_user_id BIGINT NOT NULL,
 policy_id BIGINT NULL,
 reason VARCHAR(500) NOT NULL,
 deposit_before DECIMAL(12,2) NOT NULL,
 forfeited_amount DECIMAL(12,2) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_reservation_no_show_booking(booking_id),
 CONSTRAINT fk_reservation_no_show_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_reservation_no_show_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_reservation_no_show_policy FOREIGN KEY(policy_id) REFERENCES reservation_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE deposit_settlement (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 booking_id BIGINT NOT NULL,
 kind VARCHAR(12) NOT NULL COMMENT 'REFUND or FORFEIT',
 amount DECIMAL(12,2) NOT NULL,
 status TINYINT NOT NULL COMMENT '0 refund pending, 1 paid/forfeited',
 event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_by BIGINT NOT NULL,
 processed_by BIGINT NULL,
 external_reference VARCHAR(100) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 processed_time DATETIME(6) NULL,
 UNIQUE KEY uk_deposit_settlement_event(event_key),
 KEY idx_deposit_settlement_account(account_id,status),
 CONSTRAINT fk_deposit_settlement_account FOREIGN KEY(account_id) REFERENCES reservation_deposit_account(id),
 CONSTRAINT fk_deposit_settlement_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_deposit_settlement_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_deposit_settlement_processor FOREIGN KEY(processed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TRIGGER reservation_insert_guard;
DELIMITER $$
CREATE TRIGGER reservation_insert_guard BEFORE INSERT ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5,6)
    OR NEW.reservation_source NOT IN ('CUSTOMER_PORTAL','WALK_IN','STAFF_DIRECT')
    OR (NEW.reservation_source='WALK_IN' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NULL OR NEW.staff_direct_request_key IS NOT NULL OR NEW.status<>0))
    OR (NEW.reservation_source='STAFF_DIRECT' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.staff_direct_request_key IS NULL OR NEW.walk_in_request_key IS NOT NULL OR NEW.status<>0))
    OR (NEW.reservation_source='CUSTOMER_PORTAL' AND (NEW.user_id IS NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NOT NULL OR NEW.staff_direct_request_key IS NOT NULL)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation identity';
 END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER deposit_settlement_insert_guard BEFORE INSERT ON deposit_settlement FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR LENGTH(NEW.event_key)<8
    OR NOT EXISTS(SELECT 1 FROM reservation_deposit_account a WHERE a.id=NEW.account_id AND a.booking_id=NEW.booking_id)
    OR (NEW.kind='REFUND' AND (NEW.status<>0 OR NEW.processed_by IS NOT NULL OR NEW.external_reference IS NOT NULL))
    OR (NEW.kind='FORFEIT' AND (NEW.status<>1 OR NEW.processed_by IS NOT NULL OR NEW.external_reference IS NOT NULL))
    OR NEW.kind NOT IN ('REFUND','FORFEIT') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid deposit settlement';
 END IF;
END$$
CREATE TRIGGER deposit_settlement_update_guard BEFORE UPDATE ON deposit_settlement FOR EACH ROW
BEGIN
 IF OLD.kind<>'REFUND' OR OLD.status<>0 OR NEW.status<>1 OR NEW.id<>OLD.id
    OR NEW.account_id<>OLD.account_id OR NEW.booking_id<>OLD.booking_id OR NEW.kind<>OLD.kind
    OR NEW.amount<>OLD.amount OR NEW.event_key<>OLD.event_key OR NEW.created_by<>OLD.created_by
    OR NEW.create_time<>OLD.create_time OR NEW.processed_by IS NULL
    OR NEW.external_reference IS NULL OR LENGTH(TRIM(NEW.external_reference))=0 OR NEW.processed_time IS NULL THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit settlement facts are immutable';
 END IF;
END$$
CREATE TRIGGER deposit_settlement_delete_guard BEFORE DELETE ON deposit_settlement FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit settlement facts are immutable';
END$$
CREATE TRIGGER reservation_cancellation_immutable BEFORE UPDATE ON reservation_cancellation FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cancellation facts are immutable';
END$$
CREATE TRIGGER reservation_no_show_immutable BEFORE UPDATE ON reservation_no_show FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No-show facts are immutable';
END$$
DELIMITER ;
