CREATE TABLE stayover_cleaning_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 task_id BIGINT NOT NULL,
 booking_id BIGINT NOT NULL,
 assignment_id BIGINT NOT NULL,
 room_id BIGINT NOT NULL,
 requested_by BIGINT NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 requested_time DATETIME(6) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_stayover_cleaning_task(task_id),
 UNIQUE KEY uk_stayover_cleaning_request(requested_by,request_key),
 KEY idx_stayover_booking(booking_id),
 KEY idx_stayover_assignment(assignment_id),
 CONSTRAINT fk_stayover_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),
 CONSTRAINT fk_stayover_assignment FOREIGN KEY(assignment_id,booking_id,room_id) REFERENCES booking_room_assignment(id,booking_id,room_id),
 CONSTRAINT fk_stayover_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cleaning_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 room_id BIGINT NOT NULL,
 booking_id BIGINT NULL,
 assignment_id BIGINT NULL,
 task_id BIGINT NOT NULL,
 cleaning_type TINYINT NOT NULL,
 requested_by BIGINT NULL,
 completed_by BIGINT NOT NULL,
 requested_time DATETIME(6) NULL,
 started_time DATETIME(6) NULL,
 completed_time DATETIME(6) NOT NULL,
 notes VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_cleaning_record_task(task_id),
 KEY idx_cleaning_room_time(room_id,completed_time),
 KEY idx_cleaning_booking(booking_id),
 KEY idx_cleaning_assignment(assignment_id),
 CONSTRAINT fk_cleaning_room FOREIGN KEY(room_id) REFERENCES room(id),
 CONSTRAINT fk_cleaning_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_cleaning_assignment FOREIGN KEY(assignment_id) REFERENCES booking_room_assignment(id),
 CONSTRAINT fk_cleaning_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),
 CONSTRAINT fk_cleaning_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_cleaning_completer FOREIGN KEY(completed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cleaning_record(room_id,booking_id,assignment_id,task_id,cleaning_type,completed_by,completed_time,notes)
SELECT t.room_id,t.booking_id,t.assignment_id,t.task_id,1,
       COALESCE((SELECT r.actor_user_id FROM task_record r WHERE r.task_id=t.task_id AND r.record_type IN(4,5,12) ORDER BY r.id DESC LIMIT 1),h.created_by),
       COALESCE(h.completed_time,t.completed_time,t.update_time),'Backfilled completed turnover cleaning'
FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id
WHERE h.status=2 AND NOT EXISTS(SELECT 1 FROM cleaning_record c WHERE c.task_id=t.task_id);

DELIMITER $$
CREATE TRIGGER trg_cleaning_record_no_update BEFORE UPDATE ON cleaning_record FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='cleaning_record is append-only'; END$$
CREATE TRIGGER trg_cleaning_record_no_delete BEFORE DELETE ON cleaning_record FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='cleaning_record is append-only'; END$$
DELIMITER ;
