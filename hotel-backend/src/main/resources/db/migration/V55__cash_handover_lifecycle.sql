ALTER TABLE cash_handover
 MODIFY status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 ADD COLUMN difference_type VARCHAR(8) NULL AFTER difference_amount,
 ADD COLUMN confirmed_by BIGINT NULL AFTER recorded_by,
 ADD COLUMN confirmed_at DATETIME(6) NULL AFTER confirmed_by,
 ADD COLUMN resolved_by BIGINT NULL AFTER confirmed_at,
 ADD COLUMN resolved_at DATETIME(6) NULL AFTER resolved_by,
 ADD COLUMN resolution_note VARCHAR(500) NULL AFTER resolved_at,
 ADD COLUMN update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 ADD KEY idx_cash_handover_status(status,create_time,id),
 ADD CONSTRAINT fk_cash_handover_confirmer FOREIGN KEY(confirmed_by) REFERENCES sys_user(id),
 ADD CONSTRAINT fk_cash_handover_resolver FOREIGN KEY(resolved_by) REFERENCES sys_user(id);

UPDATE cash_handover
SET difference_type=CASE WHEN difference_amount>0 THEN 'OVER' WHEN difference_amount<0 THEN 'SHORT' ELSE 'MATCHED' END;

ALTER TABLE cash_handover MODIFY difference_type VARCHAR(8) NOT NULL;

DELIMITER $$
CREATE TRIGGER trg_cash_handover_snapshot_immutable BEFORE UPDATE ON cash_handover FOR EACH ROW
BEGIN
 IF OLD.drawer_id<>NEW.drawer_id OR OLD.previous_shift_id<>NEW.previous_shift_id OR OLD.next_shift_id<>NEW.next_shift_id
    OR OLD.previous_closing_count<>NEW.previous_closing_count OR OLD.next_opening_count<>NEW.next_opening_count
    OR OLD.difference_amount<>NEW.difference_amount OR OLD.difference_type<>NEW.difference_type OR OLD.recorded_by<>NEW.recorded_by THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cash handover snapshot is immutable';
 END IF;
 IF OLD.status IN ('CONFIRMED','RESOLVED') AND NEW.status<>OLD.status THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Terminal cash handover is immutable';
 END IF;
END$$
CREATE TRIGGER trg_cash_handover_no_delete BEFORE DELETE ON cash_handover FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cash handover history is immutable';
END$$
DELIMITER ;
