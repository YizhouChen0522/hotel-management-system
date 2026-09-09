CREATE TABLE expense_registration (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    folio_id BIGINT NOT NULL,
    request_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_type VARCHAR(40) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    business_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    source_expense_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    ledger_item_id BIGINT NULL,
    registered_by BIGINT NOT NULL,
    registered_time DATETIME(6) NOT NULL,
    resolved_by BIGINT NULL,
    resolved_time DATETIME(6) NULL,
    cancel_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cancel_reason VARCHAR(500) NULL,
    UNIQUE KEY uk_expense_request(folio_id,request_key),
    UNIQUE KEY uk_expense_cancel(folio_id,cancel_key),
    UNIQUE KEY uk_expense_ledger(ledger_item_id),
    UNIQUE KEY uk_expense_folio(id,folio_id),
    CONSTRAINT fk_expense_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
    CONSTRAINT fk_expense_ledger FOREIGN KEY(ledger_item_id,folio_id) REFERENCES folio_item(id,folio_id),
    CONSTRAINT fk_expense_operator FOREIGN KEY(registered_by) REFERENCES sys_user(id),
    CONSTRAINT fk_expense_resolver FOREIGN KEY(resolved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE expense_registration ADD CONSTRAINT fk_expense_source FOREIGN KEY(source_expense_id,folio_id) REFERENCES expense_registration(id,folio_id);

DELIMITER $$
CREATE TRIGGER expense_insert_guard BEFORE INSERT ON expense_registration FOR EACH ROW
BEGIN
    IF NEW.status <> 'PENDING' OR NEW.ledger_item_id IS NOT NULL OR NEW.resolved_by IS NOT NULL OR NEW.resolved_time IS NOT NULL
       OR NEW.cancel_key IS NOT NULL OR NEW.cancel_reason IS NOT NULL
       OR NEW.item_type NOT IN ('SERVICE_CHARGE','DAMAGE_CHARGE','DISCOUNT','FEE_REVERSAL')
       OR (NEW.item_type IN ('SERVICE_CHARGE','DAMAGE_CHARGE') AND (NEW.amount <= 0 OR NEW.source_expense_id IS NOT NULL))
       OR (NEW.item_type IN ('DISCOUNT','FEE_REVERSAL') AND (NEW.amount >= 0 OR NEW.source_expense_id IS NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid expense registration';
    END IF;
END$$
CREATE TRIGGER expense_update_guard BEFORE UPDATE ON expense_registration FOR EACH ROW
BEGIN
    IF OLD.status <> 'PENDING' OR NEW.status NOT IN ('CONFIRMED','CANCELLED')
       OR NOT(NEW.folio_id <=> OLD.folio_id) OR NOT(NEW.request_key <=> OLD.request_key)
       OR NOT(NEW.item_type <=> OLD.item_type) OR NOT(NEW.amount <=> OLD.amount)
       OR NOT(NEW.business_date <=> OLD.business_date) OR NOT(NEW.description <=> OLD.description)
       OR NOT(NEW.reason <=> OLD.reason) OR NOT(NEW.source_expense_id <=> OLD.source_expense_id)
       OR NOT(NEW.registered_by <=> OLD.registered_by) OR NOT(NEW.registered_time <=> OLD.registered_time)
       OR NEW.resolved_by IS NULL OR NEW.resolved_time IS NULL
       OR (NEW.status='CONFIRMED' AND (NEW.ledger_item_id IS NULL OR NEW.cancel_key IS NOT NULL OR NEW.cancel_reason IS NOT NULL))
       OR (NEW.status='CANCELLED' AND (NEW.ledger_item_id IS NOT NULL OR NEW.cancel_key IS NULL OR NEW.cancel_reason IS NULL)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expense facts are immutable; only resolve pending registrations';
    END IF;
END$$
DELIMITER ;
