-- Wallet updates already hold the wallet row lock. Read its ledger using the
-- same current-read semantics, even when the caller has an older RR snapshot.
DROP TRIGGER wallet_update_guard;
DELIMITER $$
CREATE TRIGGER wallet_update_guard BEFORE UPDATE ON wallet FOR EACH ROW
BEGIN
    DECLARE ledger_balance DECIMAL(12,2);
    IF NEW.balance<0 OR NEW.status NOT IN (0,1) OR NEW.user_id<>OLD.user_id OR NEW.currency<>OLD.currency
       OR NEW.id<>OLD.id OR NEW.create_time<>OLD.create_time THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet mutation';
    END IF;
    IF NEW.balance<>OLD.balance THEN
        SELECT COALESCE(SUM(amount),0) INTO ledger_balance
        FROM wallet_transaction WHERE wallet_id=OLD.id FOR UPDATE;
        IF NEW.balance<>ledger_balance THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wallet balance must match its ledger';
        END IF;
    END IF;
END$$
DELIMITER ;
