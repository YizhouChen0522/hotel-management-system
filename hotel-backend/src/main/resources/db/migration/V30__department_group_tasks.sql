ALTER TABLE hotel_task
 ADD COLUMN department_id BIGINT NULL,
 ADD COLUMN department_branch TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN department_root TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN routing_source TINYINT NULL COMMENT '0 AUTO_DEPARTMENT_MANAGER, 1 MANAGEMENT_OVERRIDE',
 ADD COLUMN branch_department_key BIGINT GENERATED ALWAYS AS (CASE WHEN department_branch=1 THEN department_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_task_root_department(parent_task_id,branch_department_key),
 ADD KEY idx_task_department_open(department_id,department_branch,status),
 ADD CONSTRAINT fk_task_department FOREIGN KEY(department_id) REFERENCES department(id);
