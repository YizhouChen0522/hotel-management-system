-- Fail on inconsistent existing development data; never backfill pricing or financial history.
ALTER TABLE booking_price_version
    ADD COLUMN active_booking_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active=1 THEN booking_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_price_active_booking(active_booking_id),
    ADD UNIQUE KEY uk_price_id_booking(id,booking_id);
ALTER TABLE booking_nightly_rate
    ADD CONSTRAINT fk_nightly_version_booking FOREIGN KEY(price_version_id,booking_id) REFERENCES booking_price_version(id,booking_id);
ALTER TABLE booking_room_assignment
    MODIFY start_time DATETIME(6) NOT NULL,
    MODIFY end_time DATETIME(6) NULL,
    ADD COLUMN active_booking_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN booking_id ELSE NULL END) STORED,
    ADD COLUMN active_room_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN room_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_assignment_active_booking(active_booking_id),
    ADD UNIQUE KEY uk_assignment_active_room(active_room_id),
    ADD UNIQUE KEY uk_assignment_id_booking(id,booking_id);
ALTER TABLE booking
    ADD COLUMN reserved_room_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN (1,2) THEN assigned_room_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_booking_live_room(reserved_room_id),
    ADD KEY idx_booking_assigned_status(assigned_room_id,status);
ALTER TABLE folio ADD COLUMN closed_time DATETIME(6) NULL COMMENT 'Finalized stay account; distinct from current SETTLED balance';
ALTER TABLE folio_item
    ADD COLUMN event_key VARCHAR(100) NULL,
    ADD COLUMN room_charge_date DATE GENERATED ALWAYS AS (CASE WHEN item_type='ROOM_CHARGE' THEN business_date ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_room_charge_night(folio_id,room_assignment_id,room_charge_date),
    ADD UNIQUE KEY uk_folio_item_event(folio_id,event_key),
    ADD UNIQUE KEY uk_item_id_folio(id,folio_id);
ALTER TABLE folio_item
    ADD CONSTRAINT fk_item_source_same_folio FOREIGN KEY(source_item_id,folio_id) REFERENCES folio_item(id,folio_id);
ALTER TABLE payment MODIFY request_key VARCHAR(64) NOT NULL;
ALTER TABLE sys_audit_log MODIFY detail TEXT NULL;
CREATE TABLE room_billing_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    old_assignment_id BIGINT NOT NULL,
    new_assignment_id BIGINT NOT NULL,
    change_date DATE NOT NULL,
    new_charges_total DECIMAL(12,2) NOT NULL,
    UNIQUE KEY uk_event_old(old_assignment_id),
    UNIQUE KEY uk_event_new(new_assignment_id),
    CONSTRAINT fk_event_old_booking FOREIGN KEY(old_assignment_id,booking_id) REFERENCES booking_room_assignment(id,booking_id),
    CONSTRAINT fk_event_new_booking FOREIGN KEY(new_assignment_id,booking_id) REFERENCES booking_room_assignment(id,booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MySQL 5.7 ignores CHECK constraints, so these guards use triggers.
DELIMITER $$
CREATE TRIGGER validate_folio_item_insert BEFORE INSERT ON folio_item FOR EACH ROW
BEGIN
    IF NEW.amount=0 OR NEW.business_date IS NULL OR NEW.quantity IS NULL OR NEW.quantity<=0
        OR NEW.unit_price IS NULL OR NEW.amount<>NEW.quantity*NEW.unit_price THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid ledger amount, date or quantity';
    END IF;
END$$
CREATE TRIGGER validate_payment_insert BEFORE INSERT ON payment FOR EACH ROW
BEGIN
    IF NEW.amount<=0 OR NEW.status<>'SUCCESS' OR NEW.paid_time IS NULL OR NEW.request_key IS NULL OR LENGTH(TRIM(NEW.request_key))=0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid received payment';
    END IF;
END$$
CREATE TRIGGER immutable_folio_item BEFORE UPDATE ON folio_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ledger entries are append-only';
END$$
CREATE TRIGGER immutable_payment BEFORE UPDATE ON payment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Received payments are immutable; refunds require separate facts';
END$$
DELIMITER ;
