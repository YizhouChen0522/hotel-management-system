ALTER TABLE booking
    ADD COLUMN portal_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER staff_direct_request_key,
    ADD UNIQUE KEY uk_booking_portal_request(portal_request_key);

CREATE TABLE portal_reservation_request_lock (
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TRIGGER wallet_transaction_insert_guard;
DELIMITER $$
CREATE TRIGGER wallet_transaction_insert_guard BEFORE INSERT ON wallet_transaction FOR EACH ROW
BEGIN
    IF NEW.transaction_type NOT IN (1,2,3,5,6) OR NEW.amount=0 OR NEW.balance_before<0 OR NEW.balance_after<0
       OR NEW.balance_after<>NEW.balance_before+NEW.amount OR LENGTH(NEW.request_key)<8
       OR NOT EXISTS(SELECT 1 FROM wallet WHERE id=NEW.wallet_id AND balance=NEW.balance_before FOR UPDATE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet ledger amount or balance';
    END IF;
    IF NEW.transaction_type=1 AND (NEW.amount<=0 OR NEW.source_type<>'TOP_UP_REQUEST'
       OR NOT EXISTS(SELECT 1 FROM wallet_top_up t JOIN wallet w ON w.id=t.wallet_id
            WHERE t.id=NEW.source_id AND t.wallet_id=NEW.wallet_id AND t.status=0 AND t.amount=NEW.amount
            AND t.requested_by<>NEW.operator_user_id AND w.status=1 FOR UPDATE)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid top-up ledger source';
    END IF;
    IF NEW.transaction_type=2 AND (NEW.amount<=0 OR NEW.source_type<>'REFUND'
       OR NEW.request_key<>CONCAT('refund:',NEW.source_id)
       OR NOT EXISTS(SELECT 1 FROM refund r WHERE r.id=NEW.source_id AND r.wallet_id=NEW.wallet_id
            AND r.status=0 AND r.amount=NEW.amount AND r.destination='HOTEL_WALLET' AND r.user_id<>NEW.operator_user_id FOR UPDATE)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid refund ledger source';
    END IF;
    IF NEW.transaction_type=3 AND (NEW.amount>=0 OR NEW.source_type<>'PAYMENT'
       OR NOT EXISTS(SELECT 1 FROM payment p JOIN folio f ON f.id=p.folio_id JOIN stay s ON s.id=f.stay_id JOIN booking b ON b.id=s.booking_id JOIN wallet w ON w.user_id=b.user_id
            WHERE p.id=NEW.source_id AND p.payment_method='WALLET' AND p.status='SUCCESS' AND p.amount=-NEW.amount
            AND p.created_by=NEW.operator_user_id AND w.id=NEW.wallet_id AND w.currency=f.currency
            AND p.request_key=CONCAT('checkout-wallet:',f.id) AND NEW.request_key=p.request_key FOR UPDATE)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet payment ledger source';
    END IF;
    IF NEW.transaction_type=5 AND (NEW.amount<=0 OR NEW.source_type<>'DEPOSIT_REFUND'
       OR NEW.request_key<>CONCAT('deposit-refund:',NEW.source_id)
       OR NOT EXISTS(SELECT 1 FROM deposit_refund r WHERE r.id=NEW.source_id AND r.wallet_id=NEW.wallet_id
            AND r.status=0 AND r.amount=NEW.amount AND r.user_id<>NEW.operator_user_id AND r.requested_by<>NEW.operator_user_id FOR UPDATE)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid deposit refund ledger source';
    END IF;
    IF NEW.transaction_type=6 AND (NEW.amount>=0 OR NEW.source_type<>'DEPOSIT_PAYMENT'
       OR NOT EXISTS(SELECT 1 FROM deposit_payment p
            JOIN reservation_deposit_account a ON a.id=p.account_id
            JOIN booking b ON b.id=a.booking_id JOIN wallet w ON w.user_id=b.user_id
            WHERE p.id=NEW.source_id AND p.payment_method='WALLET' AND p.amount=-NEW.amount
            AND p.received_by=NEW.operator_user_id AND b.user_id=NEW.operator_user_id
            AND b.reservation_source='CUSTOMER_PORTAL' AND b.total_price=p.amount
            AND b.portal_request_key=p.request_key AND NEW.request_key=p.request_key
            AND w.id=NEW.wallet_id AND w.currency=a.currency FOR UPDATE)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation deposit wallet source';
    END IF;
END$$
DELIMITER ;
