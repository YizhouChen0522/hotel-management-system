ALTER TABLE folio ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE booking ADD UNIQUE KEY uk_booking_owner(id,user_id);
ALTER TABLE folio ADD UNIQUE KEY uk_folio_contract_identity(id,booking_id);
ALTER TABLE wallet ADD UNIQUE KEY uk_wallet_owner_identity(id,user_id);
ALTER TABLE payment
    ADD COLUMN wallet_checkout_folio_id BIGINT GENERATED ALWAYS AS (CASE WHEN payment_method='WALLET' THEN folio_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_payment_wallet_checkout(wallet_checkout_folio_id);

CREATE TABLE refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    folio_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING, 1 SUCCESS, 2 FAILED',
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(255) NOT NULL,
    destination VARCHAR(30) NOT NULL DEFAULT 'HOTEL_WALLET',
    requested_by BIGINT NOT NULL,
    processed_by BIGINT NULL,
    process_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    process_reason VARCHAR(255) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_time DATETIME(6) NULL,
    UNIQUE KEY uk_refund_request(folio_id,request_key),
    UNIQUE KEY uk_refund_process(folio_id,process_key),
    UNIQUE KEY uk_refund_wallet(id,wallet_id),
    CONSTRAINT fk_refund_folio FOREIGN KEY(folio_id,booking_id) REFERENCES folio(id,booking_id),
    CONSTRAINT fk_refund_owner FOREIGN KEY(booking_id,user_id) REFERENCES booking(id,user_id),
    CONSTRAINT fk_refund_wallet FOREIGN KEY(wallet_id,user_id) REFERENCES wallet(id,user_id),
    CONSTRAINT fk_refund_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
    CONSTRAINT fk_refund_processor FOREIGN KEY(processed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE wallet_transaction
    ADD COLUMN refund_source_id BIGINT GENERATED ALWAYS AS (CASE WHEN transaction_type=2 THEN source_id ELSE NULL END) STORED,
    ADD COLUMN payment_source_id BIGINT GENERATED ALWAYS AS (CASE WHEN transaction_type=3 THEN source_id ELSE NULL END) STORED,
    ADD CONSTRAINT fk_wallet_refund_source FOREIGN KEY(refund_source_id,wallet_id) REFERENCES refund(id,wallet_id),
    ADD CONSTRAINT fk_wallet_payment_source FOREIGN KEY(payment_source_id) REFERENCES payment(id);

-- Extend the existing V10 source guard; retain immutable history and non-negative, ledger-backed balances.
DROP TRIGGER wallet_transaction_insert_guard;
DELIMITER $$
CREATE TRIGGER wallet_transaction_insert_guard BEFORE INSERT ON wallet_transaction FOR EACH ROW
BEGIN
    IF NEW.transaction_type NOT IN (1,2,3) OR NEW.amount=0 OR NEW.balance_before<0 OR NEW.balance_after<0
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
END$$
CREATE TRIGGER refund_insert_guard BEFORE INSERT ON refund FOR EACH ROW
BEGIN
    IF NEW.amount<=0 OR NEW.status<>0 OR NEW.destination<>'HOTEL_WALLET' OR NEW.requested_by<>NEW.user_id
       OR NEW.processed_by IS NOT NULL OR NEW.process_key IS NOT NULL OR NEW.process_reason IS NOT NULL OR NEW.processed_time IS NOT NULL
       OR LENGTH(NEW.request_key)<8 OR LENGTH(TRIM(NEW.reason))=0
       OR NOT EXISTS(SELECT 1 FROM folio f JOIN wallet w ON w.id=NEW.wallet_id WHERE f.id=NEW.folio_id AND f.currency=NEW.currency AND w.currency=NEW.currency AND f.closed_time IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid refund request';
    END IF;
END$$
CREATE TRIGGER refund_update_guard BEFORE UPDATE ON refund FOR EACH ROW
BEGIN
    IF OLD.status<>0 OR NEW.status NOT IN (1,2) OR NEW.id<>OLD.id OR NEW.folio_id<>OLD.folio_id OR NEW.booking_id<>OLD.booking_id
       OR NEW.user_id<>OLD.user_id OR NEW.wallet_id<>OLD.wallet_id OR NEW.currency<>OLD.currency OR NEW.amount<>OLD.amount
       OR NEW.destination<>OLD.destination OR NEW.request_key<>OLD.request_key OR NEW.reason<>OLD.reason
       OR NEW.requested_by<>OLD.requested_by OR NEW.create_time<>OLD.create_time
       OR NEW.processed_by IS NULL OR NEW.processed_by=NEW.user_id OR NEW.process_key IS NULL OR LENGTH(NEW.process_key)<8
       OR NEW.process_reason IS NULL OR LENGTH(TRIM(NEW.process_reason))=0 OR NEW.processed_time IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refund facts are immutable; only pending refunds may be resolved';
    END IF;
    IF NEW.status=1 AND NOT EXISTS(SELECT 1 FROM wallet_transaction WHERE source_type='REFUND' AND source_id=NEW.id
        AND wallet_id=NEW.wallet_id AND transaction_type=2 AND amount=NEW.amount AND operator_user_id=NEW.processed_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successful refund requires its wallet credit';
    END IF;
    IF NEW.status=2 AND EXISTS(SELECT 1 FROM wallet_transaction WHERE source_type='REFUND' AND source_id=NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Credited refund cannot fail';
    END IF;
END$$
DELIMITER ;

-- V10 rejection lookup must now distinguish top-up IDs from refund/payment IDs.
DROP TRIGGER wallet_top_up_update_guard;
DELIMITER $$
CREATE TRIGGER wallet_top_up_update_guard BEFORE UPDATE ON wallet_top_up FOR EACH ROW
BEGIN
    IF OLD.status<>0 OR NEW.status NOT IN (1,2) OR NEW.id<>OLD.id OR NEW.wallet_id<>OLD.wallet_id OR NEW.amount<>OLD.amount
       OR NEW.request_key<>OLD.request_key OR NEW.requested_by<>OLD.requested_by OR NEW.create_time<>OLD.create_time
       OR NEW.resolved_by IS NULL OR NEW.resolved_by=NEW.requested_by OR NEW.resolution_key IS NULL
       OR LENGTH(NEW.resolution_key)<8 OR NEW.reason IS NULL OR LENGTH(TRIM(NEW.reason))=0 OR NEW.resolved_time IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Top-up facts are immutable; only pending requests may be resolved';
    END IF;
    IF NEW.status=1 AND NOT EXISTS(SELECT 1 FROM wallet_transaction t JOIN wallet w ON w.id=t.wallet_id
        WHERE t.source_id=NEW.id AND t.wallet_id=NEW.wallet_id AND t.amount=NEW.amount AND t.transaction_type=1
        AND t.request_key=NEW.resolution_key AND t.operator_user_id=NEW.resolved_by AND w.status=1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successful top-up requires its wallet ledger entry';
    END IF;
    IF NEW.status=2 AND EXISTS(SELECT 1 FROM wallet_transaction WHERE source_id=NEW.id AND wallet_id=NEW.wallet_id AND transaction_type=1 AND source_type='TOP_UP_REQUEST') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted top-up cannot be rejected';
    END IF;
END$$
DELIMITER ;
