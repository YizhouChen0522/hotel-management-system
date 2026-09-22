CREATE TABLE reservation_checkout_request_lock (
 customer_user_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(customer_user_id,request_key),
 CONSTRAINT fk_checkout_lock_customer FOREIGN KEY(customer_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservation_checkout_session (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 customer_user_id BIGINT NOT NULL,
 room_type_id BIGINT NOT NULL,
 check_in_date DATE NOT NULL,
 check_out_date DATE NOT NULL,
 guest_count INT NOT NULL,
 currency VARCHAR(3) NOT NULL,
 quoted_total DECIMAL(12,2) NOT NULL,
 reservation_policy_id BIGINT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(24) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_checkout_customer_request(customer_user_id,request_key),
 KEY idx_checkout_expiry(status,expires_at),
 CONSTRAINT fk_checkout_customer FOREIGN KEY(customer_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_checkout_room_type FOREIGN KEY(room_type_id) REFERENCES room_type(id),
 CONSTRAINT fk_checkout_policy FOREIGN KEY(reservation_policy_id) REFERENCES reservation_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservation_checkout_nightly_quote (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 checkout_session_id BIGINT NOT NULL,
 stay_date DATE NOT NULL,
 room_type_id BIGINT NOT NULL,
 rate_amount DECIMAL(12,2) NOT NULL,
 rate_source VARCHAR(30) NOT NULL,
 dynamic_policy_id BIGINT NULL,
 manual_override_id BIGINT NULL,
 UNIQUE KEY uk_checkout_quote_night(checkout_session_id,stay_date),
 CONSTRAINT fk_checkout_quote_session FOREIGN KEY(checkout_session_id) REFERENCES reservation_checkout_session(id),
 CONSTRAINT fk_checkout_quote_room_type FOREIGN KEY(room_type_id) REFERENCES room_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 checkout_session_id BIGINT NOT NULL,
 purpose VARCHAR(40) NOT NULL,
 provider VARCHAR(30) NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 merchant_payment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_payment_id VARCHAR(100) NULL,
 amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 status VARCHAR(24) NOT NULL,
 fulfillment_status VARCHAR(30) NOT NULL,
 booking_id BIGINT NULL,
 initiator_type VARCHAR(20) NOT NULL,
 initiated_by_user_id BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 completed_at DATETIME(6) NULL,
 failure_code VARCHAR(64) NULL,
 failure_message VARCHAR(255) NULL,
 UNIQUE KEY uk_attempt_checkout_request(checkout_session_id,request_key),
 UNIQUE KEY uk_attempt_merchant_no(merchant_payment_no),
 UNIQUE KEY uk_attempt_provider_payment(provider,provider_payment_id),
 UNIQUE KEY uk_attempt_booking(booking_id),
 KEY idx_attempt_recovery(status,fulfillment_status,updated_at),
 CONSTRAINT fk_attempt_checkout FOREIGN KEY(checkout_session_id) REFERENCES reservation_checkout_session(id),
 CONSTRAINT fk_attempt_initiator FOREIGN KEY(initiated_by_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_attempt_booking FOREIGN KEY(booking_id) REFERENCES booking(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_webhook_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 provider VARCHAR(30) NOT NULL,
 provider_event_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_type VARCHAR(40) NOT NULL,
 provider_payment_id VARCHAR(100) NULL,
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 processing_status VARCHAR(24) NOT NULL,
 received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 processed_at DATETIME(6) NULL,
 UNIQUE KEY uk_webhook_provider_event(provider,provider_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hotel_transaction_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 direction VARCHAR(24) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 source_type VARCHAR(40) NOT NULL,
 source_id BIGINT NOT NULL,
 business_reference_type VARCHAR(40) NULL,
 business_reference_id BIGINT NULL,
 payment_method VARCHAR(30) NOT NULL,
 channel VARCHAR(30) NOT NULL,
 provider VARCHAR(30) NULL,
 provider_transaction_id VARCHAR(100) NULL,
 initiator_type VARCHAR(20) NOT NULL,
 operator_user_id BIGINT NULL,
 description VARCHAR(255) NULL,
 occurred_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_transaction_source(source_type,source_id),
 KEY idx_transaction_occurred(occurred_at,id),
 KEY idx_transaction_business(business_reference_type,business_reference_id),
 KEY idx_transaction_provider_ref(provider,provider_transaction_id),
 CONSTRAINT fk_hotel_tx_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TRIGGER deposit_payment_insert_guard;
DELIMITER $$
CREATE TRIGGER deposit_payment_insert_guard BEFORE INSERT ON deposit_payment FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR NEW.payment_method NOT IN ('CASH','CARD','BANK_TRANSFER','WALLET','MOCK','WECHAT_PAY','ALIPAY','STRIPE','ADYEN') OR LENGTH(NEW.request_key)<8 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation deposit receipt';
 END IF;
 IF NEW.payment_method='WALLET' AND NOT EXISTS(
     SELECT 1 FROM reservation_deposit_account a JOIN booking b ON b.id=a.booking_id
     JOIN wallet w ON w.user_id=b.user_id
     WHERE a.id=NEW.account_id AND b.reservation_source='CUSTOMER_PORTAL'
       AND b.portal_request_key=NEW.request_key AND b.total_price=NEW.amount
       AND NEW.received_by=b.user_id AND w.currency=a.currency AND w.balance>=NEW.amount FOR UPDATE
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid portal wallet deposit receipt';
 END IF;
 IF NEW.payment_method IN ('MOCK','WECHAT_PAY','ALIPAY','STRIPE','ADYEN') AND NOT EXISTS(
     SELECT 1 FROM payment_attempt p JOIN reservation_checkout_session c ON c.id=p.checkout_session_id
     JOIN reservation_deposit_account a ON a.booking_id=p.booking_id
     WHERE a.id=NEW.account_id AND p.id=NEW.reference_no AND p.status='SUCCEEDED'
       AND p.amount=NEW.amount AND p.currency=a.currency AND p.provider=NEW.payment_method
       AND p.booking_id IS NOT NULL AND c.customer_user_id=NEW.received_by
 ) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid external reservation deposit receipt';
 END IF;
END$$
CREATE TRIGGER hotel_transaction_record_update_guard BEFORE UPDATE ON hotel_transaction_record FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hotel transaction records are append-only';
END$$
CREATE TRIGGER hotel_transaction_record_delete_guard BEFORE DELETE ON hotel_transaction_record FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hotel transaction records are append-only';
END$$
DELIMITER ;
