-- New identities only. Existing reservation/folio ownership is switched by the following forward migration.
CREATE TABLE stay (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL,
 primary_guest_id BIGINT NOT NULL,
 status TINYINT NOT NULL COMMENT '1 IN_HOUSE, 2 CHECKED_OUT',
 actual_check_in_time DATETIME(6) NOT NULL,
 actual_check_out_time DATETIME(6) NULL,
 checked_in_by BIGINT NOT NULL,
 checked_out_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_stay_booking(booking_id),
 UNIQUE KEY uk_stay_reservation_identity(id,booking_id),
 KEY idx_stay_status_arrival(status,actual_check_in_time,id),
 CONSTRAINT fk_stay_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_stay_primary_guest FOREIGN KEY(primary_guest_id) REFERENCES guest_profile(id),
 CONSTRAINT fk_stay_checkin_actor FOREIGN KEY(checked_in_by) REFERENCES sys_user(id),
 CONSTRAINT fk_stay_checkout_actor FOREIGN KEY(checked_out_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stay_guest (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 stay_id BIGINT NOT NULL,
 guest_id BIGINT NOT NULL,
 guest_role TINYINT NOT NULL COMMENT '0 PRIMARY, 1 ACCOMPANYING',
 primary_stay_id BIGINT GENERATED ALWAYS AS (CASE WHEN guest_role=0 THEN stay_id ELSE NULL END) STORED,
 registered_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_stay_guest(stay_id,guest_id),
 UNIQUE KEY uk_stay_primary_guest(primary_stay_id),
 KEY idx_stay_guest_history(guest_id,stay_id),
 CONSTRAINT fk_actual_guest_stay FOREIGN KEY(stay_id) REFERENCES stay(id),
 CONSTRAINT fk_actual_guest_profile FOREIGN KEY(guest_id) REFERENCES guest_profile(id),
 CONSTRAINT fk_actual_guest_actor FOREIGN KEY(registered_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservation_deposit_account (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL,
 currency VARCHAR(3) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_deposit_booking(booking_id),
 CONSTRAINT fk_deposit_booking FOREIGN KEY(booking_id) REFERENCES booking(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE deposit_payment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 payment_method VARCHAR(30) NOT NULL,
 reference_no VARCHAR(100) NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 received_by BIGINT NOT NULL,
 received_time DATETIME(6) NOT NULL,
 UNIQUE KEY uk_deposit_receipt_request(account_id,request_key),
 CONSTRAINT fk_deposit_payment_account FOREIGN KEY(account_id) REFERENCES reservation_deposit_account(id),
 CONSTRAINT fk_deposit_payment_actor FOREIGN KEY(received_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE deposit_refund (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 wallet_id BIGINT NOT NULL,
 currency VARCHAR(3) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING, 1 SUCCESS, 2 FAILED',
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(255) NOT NULL,
 requested_by BIGINT NOT NULL,
 processed_by BIGINT NULL,
 process_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 process_reason VARCHAR(255) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 processed_time DATETIME(6) NULL,
 UNIQUE KEY uk_deposit_refund_request(account_id,request_key),
 UNIQUE KEY uk_deposit_refund_process(account_id,process_key),
 UNIQUE KEY uk_deposit_refund_wallet(id,wallet_id),
 CONSTRAINT fk_deposit_refund_account FOREIGN KEY(account_id) REFERENCES reservation_deposit_account(id),
 CONSTRAINT fk_deposit_refund_wallet FOREIGN KEY(wallet_id,user_id) REFERENCES wallet(id,user_id),
 CONSTRAINT fk_deposit_refund_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_deposit_refund_processor FOREIGN KEY(processed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE deposit_transfer (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 stay_id BIGINT NOT NULL,
 folio_id BIGINT NOT NULL,
 payment_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 transferred_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_deposit_transfer_account(account_id),
 UNIQUE KEY uk_deposit_transfer_stay(stay_id),
 UNIQUE KEY uk_deposit_transfer_folio(folio_id),
 UNIQUE KEY uk_deposit_transfer_payment(payment_id),
 UNIQUE KEY uk_deposit_transfer_event(event_key),
 CONSTRAINT fk_deposit_transfer_account FOREIGN KEY(account_id) REFERENCES reservation_deposit_account(id),
 CONSTRAINT fk_deposit_transfer_stay FOREIGN KEY(stay_id) REFERENCES stay(id),
 CONSTRAINT fk_deposit_transfer_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
 CONSTRAINT fk_deposit_transfer_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
 CONSTRAINT fk_deposit_transfer_actor FOREIGN KEY(transferred_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
CREATE TRIGGER deposit_payment_immutable BEFORE UPDATE ON deposit_payment FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit receipts are immutable';
END$$
CREATE TRIGGER deposit_transfer_immutable BEFORE UPDATE ON deposit_transfer FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit transfers are immutable';
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER deposit_payment_insert_guard BEFORE INSERT ON deposit_payment FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR NEW.payment_method NOT IN ('CASH','CARD','BANK_TRANSFER') OR LENGTH(NEW.request_key)<8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation deposit receipt';
 END IF;
END$$
CREATE TRIGGER deposit_refund_insert_guard BEFORE INSERT ON deposit_refund FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR NEW.status<>0 OR NEW.requested_by<>NEW.user_id OR LENGTH(NEW.request_key)<8
    OR LENGTH(TRIM(NEW.reason))=0 OR NEW.processed_by IS NOT NULL OR NEW.process_key IS NOT NULL
    OR NEW.processed_time IS NOT NULL OR NEW.process_reason IS NOT NULL
    OR NOT EXISTS(SELECT 1 FROM reservation_deposit_account a JOIN booking b ON b.id=a.booking_id
        JOIN wallet w ON w.id=NEW.wallet_id WHERE a.id=NEW.account_id AND b.user_id=NEW.user_id
        AND w.user_id=NEW.user_id AND w.currency=a.currency AND NEW.currency=a.currency) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid deposit refund identity';
 END IF;
END$$
CREATE TRIGGER deposit_refund_update_guard BEFORE UPDATE ON deposit_refund FOR EACH ROW
BEGIN
 IF OLD.status<>0 OR NEW.status NOT IN (1,2) OR NEW.id<>OLD.id OR NEW.account_id<>OLD.account_id
    OR NEW.user_id<>OLD.user_id OR NEW.wallet_id<>OLD.wallet_id OR NEW.currency<>OLD.currency
    OR NEW.amount<>OLD.amount OR NEW.request_key<>OLD.request_key OR NEW.reason<>OLD.reason
    OR NEW.requested_by<>OLD.requested_by OR NEW.create_time<>OLD.create_time
    OR NEW.processed_by IS NULL OR NEW.processed_by=NEW.user_id OR NEW.processed_by=NEW.requested_by
    OR NEW.process_key IS NULL OR LENGTH(NEW.process_key)<8 OR NEW.process_reason IS NULL
    OR LENGTH(TRIM(NEW.process_reason))=0 OR NEW.processed_time IS NULL THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit refund facts are immutable';
 END IF;
 IF NEW.status=1 AND NOT EXISTS(SELECT 1 FROM wallet_transaction t WHERE t.transaction_type=5
    AND t.source_type='DEPOSIT_REFUND' AND t.source_id=NEW.id AND t.wallet_id=NEW.wallet_id
    AND t.amount=NEW.amount AND t.operator_user_id=NEW.processed_by) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Deposit refund requires wallet posting';
 END IF;
END$$
DELIMITER ;

ALTER TABLE wallet_transaction ADD COLUMN deposit_refund_source_id BIGINT GENERATED ALWAYS AS (CASE WHEN transaction_type=5 THEN source_id ELSE NULL END) STORED,
 ADD CONSTRAINT fk_wallet_deposit_refund FOREIGN KEY(deposit_refund_source_id,wallet_id) REFERENCES deposit_refund(id,wallet_id);
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
       OR NOT EXISTS(SELECT 1 FROM payment p JOIN folio f ON f.id=p.folio_id JOIN booking b ON b.id=f.booking_id JOIN wallet w ON w.user_id=b.user_id
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
