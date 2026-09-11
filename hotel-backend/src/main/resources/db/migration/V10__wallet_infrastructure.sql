CREATE TABLE wallet (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 BLOCKED (top-up only), 1 ACTIVE',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_wallet_user(user_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_top_up (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 PENDING, 1 SUCCESS, 2 REJECTED',
    requested_by BIGINT NOT NULL,
    resolved_by BIGINT NULL,
    resolution_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason VARCHAR(255) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_time DATETIME(6) NULL,
    UNIQUE KEY uk_wallet_top_up_request(wallet_id,request_key),
    UNIQUE KEY uk_wallet_top_up_resolution(wallet_id,resolution_key),
    UNIQUE KEY uk_top_up_wallet(id,wallet_id),
    CONSTRAINT fk_top_up_wallet FOREIGN KEY(wallet_id) REFERENCES wallet(id) ON DELETE RESTRICT,
    CONSTRAINT fk_top_up_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_top_up_resolver FOREIGN KEY(resolved_by) REFERENCES sys_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    transaction_type TINYINT NOT NULL COMMENT '1 TOP_UP; 2/3/4 reserved for later phases',
    amount DECIMAL(12,2) NOT NULL,
    balance_before DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    top_up_source_id BIGINT GENERATED ALWAYS AS (CASE WHEN transaction_type=1 THEN source_id ELSE NULL END) STORED,
    operator_user_id BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_wallet_transaction_request(wallet_id,request_key),
    UNIQUE KEY uk_wallet_transaction_source(source_type,source_id),
    KEY idx_wallet_transaction_page(wallet_id,id),
    CONSTRAINT fk_transaction_wallet FOREIGN KEY(wallet_id) REFERENCES wallet(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_top_up FOREIGN KEY(top_up_source_id,wallet_id) REFERENCES wallet_top_up(id,wallet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_operator FOREIGN KEY(operator_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Anti-join plus the unique user constraint makes the zero-balance backfill repeat-safe.
-- CNY matches the current hotel's configured currency; wallets never follow a booking lifecycle.
INSERT INTO wallet(user_id,currency)
SELECT u.id,'CNY' FROM sys_user u LEFT JOIN wallet w ON w.user_id=u.id WHERE w.id IS NULL;

DELIMITER $$
CREATE TRIGGER wallet_insert_guard BEFORE INSERT ON wallet FOR EACH ROW
BEGIN
    IF NEW.balance<>0 OR NEW.status<>1 OR NEW.currency<>'CNY' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='New wallets must be active zero-balance CNY accounts';
    END IF;
END$$
CREATE TRIGGER wallet_update_guard BEFORE UPDATE ON wallet FOR EACH ROW
BEGIN
    IF NEW.balance<0 OR NEW.status NOT IN (0,1) OR NEW.user_id<>OLD.user_id OR NEW.currency<>OLD.currency
       OR NEW.id<>OLD.id OR NEW.create_time<>OLD.create_time THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet mutation';
    END IF;
    IF NEW.balance<>OLD.balance AND NEW.balance<>(SELECT COALESCE(SUM(amount),0) FROM wallet_transaction WHERE wallet_id=OLD.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wallet balance must match its ledger';
    END IF;
END$$
CREATE TRIGGER wallet_top_up_insert_guard BEFORE INSERT ON wallet_top_up FOR EACH ROW
BEGIN
    IF NEW.amount<=0 OR NEW.status<>0 OR NEW.resolved_by IS NOT NULL OR NEW.resolution_key IS NOT NULL
       OR NEW.resolved_time IS NOT NULL OR NEW.reason IS NOT NULL OR LENGTH(NEW.request_key)<8
       OR NOT EXISTS(SELECT 1 FROM wallet WHERE id=NEW.wallet_id AND user_id=NEW.requested_by AND status=1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid top-up request';
    END IF;
END$$
CREATE TRIGGER wallet_transaction_insert_guard BEFORE INSERT ON wallet_transaction FOR EACH ROW
BEGIN
    IF NEW.transaction_type<>1 OR NEW.amount<=0 OR NEW.balance_before<0 OR NEW.balance_after<>NEW.balance_before+NEW.amount
       OR NEW.source_type<>'TOP_UP_REQUEST' OR LENGTH(NEW.request_key)<8
       OR NOT EXISTS(SELECT 1 FROM wallet_top_up t JOIN wallet w ON w.id=t.wallet_id
            WHERE t.id=NEW.source_id AND t.wallet_id=NEW.wallet_id AND t.status=0 AND t.amount=NEW.amount
            AND t.requested_by<>NEW.operator_user_id AND w.status=1 AND w.balance=NEW.balance_before) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid wallet ledger source or amount';
    END IF;
END$$
CREATE TRIGGER wallet_transaction_immutable BEFORE UPDATE ON wallet_transaction FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wallet ledger is append-only';
END$$
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
    IF NEW.status=2 AND EXISTS(SELECT 1 FROM wallet_transaction WHERE source_id=NEW.id AND wallet_id=NEW.wallet_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Posted top-up cannot be rejected';
    END IF;
END$$
DELIMITER ;
