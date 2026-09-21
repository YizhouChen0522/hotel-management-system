DROP TRIGGER deposit_payment_insert_guard;
DELIMITER $$
CREATE TRIGGER deposit_payment_insert_guard BEFORE INSERT ON deposit_payment FOR EACH ROW
BEGIN
 IF NEW.amount<=0 OR NEW.payment_method NOT IN ('CASH','CARD','BANK_TRANSFER','WALLET') OR LENGTH(NEW.request_key)<8 THEN
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
END$$
DELIMITER ;
