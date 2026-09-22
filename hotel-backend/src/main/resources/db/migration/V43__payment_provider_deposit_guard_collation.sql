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
 IF NEW.payment_method IN ('MOCK','WECHAT_PAY','ALIPAY','STRIPE','ADYEN') THEN
  IF NOT EXISTS(
      SELECT 1 FROM payment_attempt p JOIN reservation_checkout_session c ON c.id=p.checkout_session_id
      JOIN reservation_deposit_account a ON a.booking_id=p.booking_id
      WHERE a.id=NEW.account_id AND p.id=NEW.reference_no AND p.status='SUCCEEDED'
        AND p.amount=NEW.amount AND p.currency=a.currency
        AND p.provider=CONVERT(NEW.payment_method USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND p.booking_id IS NOT NULL AND c.customer_user_id=NEW.received_by
  ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid external reservation deposit receipt';
  END IF;
 END IF;
END$$
DELIMITER ;
