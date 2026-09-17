-- The development-only legacy cleanup is performed explicitly before this migration.
-- No historical money or reservation data is silently reinterpreted here.
ALTER TABLE stay ADD COLUMN registration_id BIGINT NOT NULL,
 ADD UNIQUE KEY uk_stay_registration(registration_id),
 ADD CONSTRAINT fk_stay_registration FOREIGN KEY(registration_id) REFERENCES guest_registration(id);

ALTER TABLE refund DROP FOREIGN KEY fk_refund_folio;
ALTER TABLE stay_adjustment DROP FOREIGN KEY fk_stay_folio, DROP FOREIGN KEY fk_stay_assignment, DROP FOREIGN KEY fk_stay_previous;
ALTER TABLE stay_extension_nightly_rate DROP FOREIGN KEY fk_extension_adjustment;
ALTER TABLE room_billing_event DROP FOREIGN KEY fk_event_old_booking, DROP FOREIGN KEY fk_event_new_booking;
ALTER TABLE room_turnover_task DROP FOREIGN KEY fk_turnover_assignment, DROP FOREIGN KEY fk_turnover_booking;
ALTER TABLE stayover_cleaning_request DROP FOREIGN KEY fk_stayover_assignment;

RENAME TABLE booking_room_assignment TO stay_room_assignment;
ALTER TABLE stay_room_assignment DROP FOREIGN KEY fk_assignment_booking,
 DROP INDEX uk_assignment_active_booking, DROP COLUMN active_booking_id,
 DROP INDEX uk_assignment_id_booking, DROP INDEX uk_assignment_booking_room,
 DROP INDEX idx_assignment_booking_end_time,
 CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD COLUMN active_stay_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN stay_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_assignment_active_stay(active_stay_id),
 ADD UNIQUE KEY uk_assignment_id_stay(id,stay_id),
 ADD UNIQUE KEY uk_assignment_stay_room(id,stay_id,room_id),
 ADD KEY idx_assignment_stay_end(stay_id,end_time),
 ADD CONSTRAINT fk_assignment_stay FOREIGN KEY(stay_id) REFERENCES stay(id);

ALTER TABLE folio DROP FOREIGN KEY fk_folio_booking,
 DROP INDEX uk_folio_booking, DROP INDEX uk_folio_contract_identity,
 CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD UNIQUE KEY uk_folio_stay(stay_id), ADD UNIQUE KEY uk_folio_stay_identity(id,stay_id),
 ADD CONSTRAINT fk_folio_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE refund ADD CONSTRAINT fk_refund_folio FOREIGN KEY(folio_id) REFERENCES folio(id);

ALTER TABLE stay_adjustment CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 RENAME INDEX uk_stay_id_booking TO uk_adjustment_id_stay,
 ADD CONSTRAINT fk_stay_folio FOREIGN KEY(folio_id,stay_id) REFERENCES folio(id,stay_id),
 ADD CONSTRAINT fk_stay_assignment FOREIGN KEY(assignment_id,stay_id) REFERENCES stay_room_assignment(id,stay_id);
ALTER TABLE stay_adjustment ADD CONSTRAINT fk_stay_previous FOREIGN KEY(previous_late_id,stay_id) REFERENCES stay_adjustment(id,stay_id);
ALTER TABLE stay_extension_nightly_rate CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_extension_adjustment FOREIGN KEY(adjustment_id,stay_id) REFERENCES stay_adjustment(id,stay_id);
ALTER TABLE room_billing_event CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_event_old_stay FOREIGN KEY(old_assignment_id,stay_id) REFERENCES stay_room_assignment(id,stay_id),
 ADD CONSTRAINT fk_event_new_stay FOREIGN KEY(new_assignment_id,stay_id) REFERENCES stay_room_assignment(id,stay_id);
ALTER TABLE room_turnover_task CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_turnover_assignment FOREIGN KEY(assignment_id,stay_id,room_id) REFERENCES stay_room_assignment(id,stay_id,room_id),
 ADD CONSTRAINT fk_turnover_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE stayover_cleaning_request CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_stayover_assignment FOREIGN KEY(assignment_id,stay_id,room_id) REFERENCES stay_room_assignment(id,stay_id,room_id);
ALTER TABLE cleaning_record DROP FOREIGN KEY fk_cleaning_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_cleaning_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE housekeeping_inspection DROP FOREIGN KEY fk_inspection_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_inspection_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE rework_cleaning_request DROP FOREIGN KEY fk_rework_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_rework_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE guest_service_order DROP FOREIGN KEY fk_service_order_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_service_order_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE guest_purchase DROP FOREIGN KEY fk_purchase_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_purchase_stay FOREIGN KEY(stay_id) REFERENCES stay(id);
ALTER TABLE damage_assessment DROP FOREIGN KEY fk_damage_booking, CHANGE COLUMN booking_id stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_damage_stay FOREIGN KEY(stay_id) REFERENCES stay(id);

ALTER TABLE stay_history DROP FOREIGN KEY fk_stay_history_user,
 DROP INDEX idx_stay_history_user_id, DROP COLUMN user_id,
 DROP COLUMN actual_check_in_time, DROP COLUMN actual_check_out_time,
 ADD COLUMN stay_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_stay_history_stay FOREIGN KEY(stay_id) REFERENCES stay(id);

DROP TRIGGER preserve_checked_in_contract;
ALTER TABLE booking DROP INDEX uk_booking_live_room, DROP COLUMN reserved_room_id,
 DROP INDEX idx_booking_assigned_status,
 CHANGE COLUMN assigned_room_id reserved_room_id BIGINT NULL COMMENT 'Pre-arrival physical room reservation only',
 MODIFY user_id BIGINT NULL,
 MODIFY status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING, 1 APPROVED, 4 CANCELLED, 5 REJECTED',
 ADD COLUMN reservation_source VARCHAR(30) NOT NULL DEFAULT 'DIRECT',
 ADD KEY idx_booking_reserved_status(reserved_room_id,status);

DELIMITER $$
CREATE TRIGGER preserve_reservation_contract BEFORE UPDATE ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reservation status cannot describe occupancy';
 END IF;
 IF EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=OLD.id) AND
    (NOT(OLD.check_in_date<=>NEW.check_in_date) OR NOT(OLD.check_out_date<=>NEW.check_out_date)
     OR NOT(OLD.room_type_id<=>NEW.room_type_id) OR NOT(OLD.total_price<=>NEW.total_price)
     OR NOT(OLD.guest_count<=>NEW.guest_count) OR NOT(OLD.user_id<=>NEW.user_id)
     OR OLD.status<>NEW.status OR NOT(OLD.reserved_room_id<=>NEW.reserved_room_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Arrived reservation contract is immutable';
 END IF;
END$$
CREATE TRIGGER stay_lifecycle_guard BEFORE UPDATE ON stay FOR EACH ROW
BEGIN
 IF OLD.status<>1 OR NEW.status<>2 OR NEW.actual_check_out_time IS NULL
    OR NEW.actual_check_out_time<OLD.actual_check_in_time OR NEW.checked_out_by IS NULL
    OR NEW.id<>OLD.id OR NEW.booking_id<>OLD.booking_id OR NEW.primary_guest_id<>OLD.primary_guest_id
    OR NEW.registration_id<>OLD.registration_id OR NEW.actual_check_in_time<>OLD.actual_check_in_time
    OR NEW.checked_in_by<>OLD.checked_in_by OR NEW.create_time<>OLD.create_time THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid actual Stay transition';
 END IF;
END$$
DELIMITER ;

DROP TRIGGER wallet_transaction_insert_guard;
DELIMITER $$
CREATE TRIGGER wallet_transaction_insert_guard BEFORE INSERT ON wallet_transaction FOR EACH ROW
BEGIN
    IF NEW.transaction_type NOT IN (1,2,3,5) OR NEW.amount=0 OR NEW.balance_before<0 OR NEW.balance_after<0
       OR NEW.balance_after<>NEW.balance_before+NEW.amount OR LENGTH(NEW.request_key)<8
       OR NOT EXISTS(SELECT 1 FROM wallet WHERE id=NEW.wallet_id AND balance=NEW.balance_before) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet ledger amount or balance';
    END IF;
    IF NEW.transaction_type=1 AND (NEW.amount<=0 OR NEW.source_type<>'TOP_UP_REQUEST'
       OR NOT EXISTS(SELECT 1 FROM wallet_top_up t JOIN wallet w ON w.id=t.wallet_id
            WHERE t.id=NEW.source_id AND t.wallet_id=NEW.wallet_id AND t.status=0 AND t.amount=NEW.amount
            AND t.requested_by<>NEW.operator_user_id AND w.status=1)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid top-up ledger source';
    END IF;
    IF NEW.transaction_type=2 AND (NEW.amount<=0 OR NEW.source_type<>'REFUND'
       OR NEW.request_key<>CONCAT('refund:',NEW.source_id)
       OR NOT EXISTS(SELECT 1 FROM refund r WHERE r.id=NEW.source_id AND r.wallet_id=NEW.wallet_id
            AND r.status=0 AND r.amount=NEW.amount AND r.destination='HOTEL_WALLET' AND r.user_id<>NEW.operator_user_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid refund ledger source';
    END IF;
    IF NEW.transaction_type=3 AND (NEW.amount>=0 OR NEW.source_type<>'PAYMENT'
       OR NOT EXISTS(SELECT 1 FROM payment p JOIN folio f ON f.id=p.folio_id JOIN stay s ON s.id=f.stay_id JOIN booking b ON b.id=s.booking_id JOIN wallet w ON w.user_id=b.user_id
            WHERE p.id=NEW.source_id AND p.payment_method='WALLET' AND p.status='SUCCESS' AND p.amount=-NEW.amount
            AND p.created_by=NEW.operator_user_id AND w.id=NEW.wallet_id AND w.currency=f.currency
            AND p.request_key=CONCAT('checkout-wallet:',f.id) AND NEW.request_key=p.request_key)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet payment ledger source';
    END IF;
    IF NEW.transaction_type=5 AND (NEW.amount<=0 OR NEW.source_type<>'DEPOSIT_REFUND'
       OR NEW.request_key<>CONCAT('deposit-refund:',NEW.source_id)
       OR NOT EXISTS(SELECT 1 FROM deposit_refund r WHERE r.id=NEW.source_id AND r.wallet_id=NEW.wallet_id
            AND r.status=0 AND r.amount=NEW.amount AND r.user_id<>NEW.operator_user_id AND r.requested_by<>NEW.operator_user_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid deposit refund ledger source';
    END IF;
END$$
DELIMITER ;


DELIMITER $$
CREATE TRIGGER reservation_insert_guard BEFORE INSERT ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation status';
 END IF;
END$$
CREATE TRIGGER actual_stay_insert_guard BEFORE INSERT ON stay FOR EACH ROW
BEGIN
 IF NEW.status<>1 OR NEW.actual_check_out_time IS NOT NULL OR NEW.checked_out_by IS NOT NULL
    OR NOT EXISTS(SELECT 1 FROM booking b JOIN guest_registration g ON g.booking_id=b.id
      WHERE b.id=NEW.booking_id AND b.status=1 AND g.id=NEW.registration_id
       AND g.registration_confirmed=1 AND g.primary_guest_id=NEW.primary_guest_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Actual Stay requires an approved registered arrival';
 END IF;
END$$
CREATE TRIGGER deposit_transfer_insert_guard BEFORE INSERT ON deposit_transfer FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR NEW.event_key<>CONCAT('deposit-transfer:',NEW.account_id)
    OR NOT EXISTS(SELECT 1 FROM reservation_deposit_account d JOIN stay s ON s.booking_id=d.booking_id
       JOIN folio f ON f.stay_id=s.id JOIN payment p ON p.folio_id=f.id
       WHERE d.id=NEW.account_id AND s.id=NEW.stay_id AND s.status=1 AND f.id=NEW.folio_id
        AND f.closed_time IS NULL AND f.currency=d.currency AND p.id=NEW.payment_id
        AND p.amount=NEW.amount AND p.payment_method='DEPOSIT_TRANSFER' AND p.status='SUCCESS'
        AND p.request_key=NEW.event_key AND p.created_by=NEW.transferred_by)
    OR NEW.amount > (SELECT COALESCE(SUM(amount),0) FROM deposit_payment WHERE account_id=NEW.account_id)
        - (SELECT COALESCE(SUM(amount),0) FROM deposit_refund WHERE account_id=NEW.account_id AND status IN (0,1)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation prepayment transfer';
 END IF;
END$$
DELIMITER ;
