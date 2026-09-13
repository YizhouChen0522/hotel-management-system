ALTER TABLE hotel_task
 ADD COLUMN execution_type TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN parent_task_id BIGINT NULL,
 ADD KEY idx_task_parent (parent_task_id),
 ADD CONSTRAINT fk_task_parent FOREIGN KEY (parent_task_id) REFERENCES hotel_task(id);
UPDATE hotel_task SET execution_type=2 WHERE assignment_mode=2;

ALTER TABLE task_assignment
 ADD UNIQUE KEY uk_assignment_identity (id,task_id,assignee_user_id),
 ADD COLUMN active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_current=1 THEN assignee_user_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_assignment_active (task_id,active_user_id);

CREATE TABLE todo (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 task_id BIGINT NOT NULL,
 assignment_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 is_active TINYINT NOT NULL DEFAULT 1,
 active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active=1 THEN user_id ELSE NULL END) STORED,
 acknowledged_time DATETIME(6) NULL,
 completed_time DATETIME(6) NULL,
 ended_time DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_todo_assignment (assignment_id),
 UNIQUE KEY uk_todo_active (task_id,active_user_id),
 KEY idx_todo_owner (user_id,is_active,status),
 CONSTRAINT fk_todo_assignment FOREIGN KEY (assignment_id,task_id,user_id) REFERENCES task_assignment(id,task_id,assignee_user_id),
 CONSTRAINT fk_todo_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO todo(task_id,assignment_id,user_id,status,is_active,acknowledged_time,completed_time,ended_time,create_time)
SELECT task_id,id,assignee_user_id,status,is_current,accepted_time,completed_time,ended_time,create_time FROM task_assignment;
UPDATE hotel_task h SET status=1 WHERE h.status=0 AND EXISTS(SELECT 1 FROM task_assignment a WHERE a.task_id=h.id AND a.is_current=1);
INSERT INTO task_record(task_id,assignment_id,record_type,actor_user_id,detail)
SELECT task_id,id,8,assigned_by,'V16 migrated personal execution state' FROM task_assignment;
