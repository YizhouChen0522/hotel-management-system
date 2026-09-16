ALTER TABLE room_repair_task
 ADD COLUMN issue_description VARCHAR(500) NULL AFTER room_id,
 ADD COLUMN priority TINYINT NOT NULL DEFAULT 2 AFTER issue_description,
 ADD COLUMN status TINYINT NOT NULL DEFAULT 0 AFTER priority,
 ADD COLUMN estimated_cost DECIMAL(12,2) NULL AFTER status,
 ADD COLUMN actual_cost DECIMAL(12,2) NULL AFTER estimated_cost,
 ADD COLUMN started_time DATETIME(6) NULL AFTER actual_cost,
 ADD COLUMN completed_time DATETIME(6) NULL AFTER started_time,
 ADD COLUMN completed_by BIGINT NULL AFTER completed_time,
 ADD COLUMN notes VARCHAR(500) NULL AFTER completed_by,
 ADD COLUMN update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER create_time,
 ADD KEY idx_repair_status_time(status,create_time,id),
 ADD CONSTRAINT fk_repair_completed_by FOREIGN KEY(completed_by) REFERENCES sys_user(id);

UPDATE room_repair_task r JOIN housekeeping_inspection i ON i.id=r.inspection_id
 JOIN hotel_task t ON t.id=r.task_id
 SET r.issue_description=COALESCE(NULLIF(i.notes,''),t.description,'Structural room damage'),
     r.status=CASE t.status WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 0 END,
     r.completed_time=CASE WHEN t.status=2 THEN t.completed_time ELSE NULL END;

ALTER TABLE room_repair_task MODIFY issue_description VARCHAR(500) NOT NULL;

ALTER TABLE housekeeping_inspection
 MODIFY cleaning_record_id BIGINT NULL,
 ADD COLUMN repair_order_id BIGINT NULL AFTER cleaning_record_id,
 ADD UNIQUE KEY uk_inspection_repair(repair_order_id),
 ADD CONSTRAINT fk_inspection_repair_order FOREIGN KEY(repair_order_id) REFERENCES room_repair_task(id);
