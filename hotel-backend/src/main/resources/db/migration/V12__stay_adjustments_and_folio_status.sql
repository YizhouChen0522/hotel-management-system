-- Financial status is derived from the materialized authoritative current-read summary.
ALTER TABLE folio ADD COLUMN financial_status TINYINT GENERATED ALWAYS AS
    (CASE WHEN balance_amount>0 THEN 0 WHEN balance_amount<0 THEN 2 ELSE 1 END) STORED;
ALTER TABLE folio DROP INDEX idx_folio_status, DROP COLUMN status;
ALTER TABLE folio CHANGE COLUMN financial_status status TINYINT GENERATED ALWAYS AS
    (CASE WHEN balance_amount>0 THEN 0 WHEN balance_amount<0 THEN 2 ELSE 1 END) STORED,
    ADD KEY idx_folio_status(status);

-- Future reservations may share a room on non-overlapping dates. Actual occupancy remains unique.
ALTER TABLE booking DROP INDEX uk_booking_live_room,
    MODIFY COLUMN reserved_room_id BIGINT GENERATED ALWAYS AS (CASE WHEN status=2 THEN assigned_room_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_booking_live_room(reserved_room_id);

CREATE TABLE stay_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    folio_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    adjustment_type TINYINT NOT NULL COMMENT '1 EXTENSION, 2 EARLY_CHECKOUT, 3 LATE_CHECKOUT',
    old_end DATE NOT NULL,
    new_end DATE NOT NULL,
    effective_time DATETIME(6) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_id BIGINT NOT NULL,
    previous_late_id BIGINT NULL,
    conflict_booking_id BIGINT NULL,
    locked_rate DECIMAL(10,2) NULL COMMENT 'Late fee pricing basis; financial amount belongs to FolioItem',
    rate_source VARCHAR(30) NULL,
    basis_item_id BIGINT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_stay_request(booking_id,request_key),
    UNIQUE KEY uk_stay_id_folio(id,folio_id),
    UNIQUE KEY uk_stay_id_booking(id,booking_id),
    CONSTRAINT fk_stay_folio FOREIGN KEY(folio_id,booking_id) REFERENCES folio(id,booking_id),
    CONSTRAINT fk_stay_assignment FOREIGN KEY(assignment_id,booking_id) REFERENCES booking_room_assignment(id,booking_id),
    CONSTRAINT fk_stay_actor FOREIGN KEY(operator_id) REFERENCES sys_user(id),
    CONSTRAINT fk_stay_conflict FOREIGN KEY(conflict_booking_id) REFERENCES booking(id),
    CONSTRAINT fk_stay_previous FOREIGN KEY(previous_late_id,booking_id) REFERENCES stay_adjustment(id,booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE stay_extension_nightly_rate (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    adjustment_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    room_type_id BIGINT NOT NULL,
    rate_amount DECIMAL(10,2) NOT NULL,
    rate_source VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    UNIQUE KEY uk_extension_night(booking_id,stay_date),
    CONSTRAINT fk_extension_adjustment FOREIGN KEY(adjustment_id,booking_id) REFERENCES stay_adjustment(id,booking_id),
    CONSTRAINT fk_extension_room_type FOREIGN KEY(room_type_id) REFERENCES room_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE folio_item ADD COLUMN stay_adjustment_id BIGINT NULL,
    ADD CONSTRAINT fk_item_stay_adjustment FOREIGN KEY(stay_adjustment_id,folio_id) REFERENCES stay_adjustment(id,folio_id);
DELIMITER $$
CREATE TRIGGER immutable_stay_adjustment BEFORE UPDATE ON stay_adjustment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stay adjustments are immutable';
END$$
CREATE TRIGGER immutable_extension_rate BEFORE UPDATE ON stay_extension_nightly_rate FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Extension price snapshots are immutable';
END$$
CREATE TRIGGER preserve_checked_in_contract BEFORE UPDATE ON booking FOR EACH ROW
BEGIN
    IF OLD.status IN (2,3) AND (NOT(OLD.check_in_date<=>NEW.check_in_date) OR NOT(OLD.check_out_date<=>NEW.check_out_date)
       OR NOT(OLD.room_type_id<=>NEW.room_type_id) OR NOT(OLD.total_price<=>NEW.total_price)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Checked-in reservation contract is immutable';
    END IF;
END$$
DELIMITER ;
