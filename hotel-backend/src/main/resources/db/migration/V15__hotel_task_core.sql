CREATE TABLE hotel_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_type TINYINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    assignment_mode TINYINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    source_key VARCHAR(100) NULL,
    request_key VARCHAR(100) NULL,
    created_by BIGINT NOT NULL,
    completed_time DATETIME(6) NULL,
    cancelled_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_hotel_task_source (source_key),
    UNIQUE KEY uk_hotel_task_request (created_by, request_key),
    KEY idx_hotel_task_status_type (status, task_type),
    CONSTRAINT fk_hotel_task_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    assignee_user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    assignment_round INT NOT NULL DEFAULT 1,
    is_current TINYINT NOT NULL DEFAULT 1,
    assigned_by BIGINT NOT NULL,
    accepted_time DATETIME(6) NULL,
    completed_time DATETIME(6) NULL,
    ended_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_task_assignment_round (task_id, assignee_user_id, assignment_round),
    KEY idx_task_assignment_todo (assignee_user_id, is_current, status),
    KEY idx_task_assignment_task_current (task_id, is_current),
    CONSTRAINT fk_task_assignment_task FOREIGN KEY (task_id) REFERENCES hotel_task(id),
    CONSTRAINT fk_task_assignment_assignee FOREIGN KEY (assignee_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_task_assignment_assigner FOREIGN KEY (assigned_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    assignment_id BIGINT NULL,
    record_type TINYINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    detail VARCHAR(500) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_task_record_task (task_id, id),
    CONSTRAINT fk_task_record_task FOREIGN KEY (task_id) REFERENCES hotel_task(id),
    CONSTRAINT fk_task_record_assignment FOREIGN KEY (assignment_id) REFERENCES task_assignment(id),
    CONSTRAINT fk_task_record_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE room_turnover_task ADD COLUMN task_id BIGINT NULL AFTER id;

INSERT INTO hotel_task(task_type,status,assignment_mode,title,description,source_key,created_by,completed_time)
SELECT 0, CASE t.status WHEN 0 THEN 0 WHEN 1 THEN 1 ELSE 2 END, 0,
       CONCAT('Room turnover #',t.id), CONCAT('Room ',t.room_id,' after assignment ',t.assignment_id),
       CONCAT('ROOM_TURNOVER:',t.id), COALESCE(t.accepted_by,t.completed_by,a.created_by), t.completed_time
FROM room_turnover_task t JOIN booking_room_assignment a ON a.id=t.assignment_id;

UPDATE room_turnover_task t JOIN hotel_task h ON h.source_key=CONCAT('ROOM_TURNOVER:',t.id)
SET t.task_id=h.id;

INSERT INTO task_assignment(task_id,assignee_user_id,status,assigned_by,accepted_time,completed_time)
SELECT t.task_id,t.accepted_by,CASE WHEN t.status=2 THEN 2 ELSE 1 END,t.accepted_by,t.accepted_time,t.completed_time
FROM room_turnover_task t WHERE t.accepted_by IS NOT NULL;

INSERT INTO task_record(task_id,record_type,actor_user_id,detail)
SELECT t.task_id,0,h.created_by,'Migrated V14 turnover task' FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id;
INSERT INTO task_record(task_id,assignment_id,record_type,actor_user_id,detail)
SELECT t.task_id,a.id,CASE WHEN t.status=2 THEN 4 ELSE 3 END,COALESCE(t.completed_by,t.accepted_by),'Migrated V14 turnover state'
FROM room_turnover_task t JOIN task_assignment a ON a.task_id=t.task_id WHERE t.accepted_by IS NOT NULL;
INSERT INTO task_record(task_id,record_type,actor_user_id,detail)
SELECT t.task_id,5,t.completed_by,'Migrated V14 direct completion'
FROM room_turnover_task t WHERE t.status=2 AND t.accepted_by IS NULL AND t.completed_by IS NOT NULL;

ALTER TABLE room_turnover_task MODIFY task_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_turnover_task (task_id),
    ADD CONSTRAINT fk_turnover_task FOREIGN KEY (task_id) REFERENCES hotel_task(id);
