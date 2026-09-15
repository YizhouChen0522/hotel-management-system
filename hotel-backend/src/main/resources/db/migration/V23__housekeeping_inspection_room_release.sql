CREATE TABLE housekeeping_inspection (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 room_id BIGINT NOT NULL, booking_id BIGINT NOT NULL, assignment_id BIGINT NOT NULL,
 turnover_task_id BIGINT NOT NULL, cleaning_record_id BIGINT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0, inspector_id BIGINT NULL,
 failure_reason TINYINT NULL, notes VARCHAR(500) NULL,
 followup_task_id BIGINT NULL, inspected_time DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_inspection_cleaning(cleaning_record_id),
 UNIQUE KEY uk_inspection_followup(followup_task_id),
 KEY idx_inspection_room(room_id,id), KEY idx_inspection_assignment(assignment_id), KEY idx_inspection_status(status),
 CONSTRAINT fk_inspection_room FOREIGN KEY(room_id) REFERENCES room(id),
 CONSTRAINT fk_inspection_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_inspection_assignment FOREIGN KEY(assignment_id) REFERENCES booking_room_assignment(id),
 CONSTRAINT fk_inspection_turnover FOREIGN KEY(turnover_task_id) REFERENCES room_turnover_task(id),
 CONSTRAINT fk_inspection_cleaning FOREIGN KEY(cleaning_record_id) REFERENCES cleaning_record(id),
 CONSTRAINT fk_inspection_inspector FOREIGN KEY(inspector_id) REFERENCES sys_user(id),
 CONSTRAINT fk_inspection_followup FOREIGN KEY(followup_task_id) REFERENCES hotel_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rework_cleaning_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL, inspection_id BIGINT NOT NULL,
 room_id BIGINT NOT NULL, booking_id BIGINT NOT NULL, assignment_id BIGINT NOT NULL,
 requested_by BIGINT NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_rework_task(task_id), UNIQUE KEY uk_rework_inspection(inspection_id),
 CONSTRAINT fk_rework_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),
 CONSTRAINT fk_rework_inspection FOREIGN KEY(inspection_id) REFERENCES housekeeping_inspection(id),
 CONSTRAINT fk_rework_room FOREIGN KEY(room_id) REFERENCES room(id),
 CONSTRAINT fk_rework_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_rework_assignment FOREIGN KEY(assignment_id) REFERENCES booking_room_assignment(id),
 CONSTRAINT fk_rework_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE room_repair_task (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL, inspection_id BIGINT NOT NULL,
 room_id BIGINT NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_room_repair_task(task_id), UNIQUE KEY uk_room_repair_inspection(inspection_id),
 KEY idx_room_repair_room(room_id),
 CONSTRAINT fk_repair_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),
 CONSTRAINT fk_repair_inspection FOREIGN KEY(inspection_id) REFERENCES housekeeping_inspection(id),
 CONSTRAINT fk_repair_room FOREIGN KEY(room_id) REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO housekeeping_inspection(room_id,booking_id,assignment_id,turnover_task_id,cleaning_record_id,status)
SELECT c.room_id,c.booking_id,c.assignment_id,t.id,c.id,0
FROM cleaning_record c JOIN room_turnover_task t ON t.task_id=c.task_id
WHERE c.cleaning_type=1 AND NOT EXISTS(SELECT 1 FROM housekeeping_inspection i WHERE i.cleaning_record_id=c.id);
