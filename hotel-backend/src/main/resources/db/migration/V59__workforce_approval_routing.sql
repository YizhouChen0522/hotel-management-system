CREATE TABLE workforce_approval_config (
    id TINYINT NOT NULL PRIMARY KEY,
    hr_department_id BIGINT NULL,
    updated_by BIGINT NOT NULL,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_workforce_config_hr_department FOREIGN KEY (hr_department_id) REFERENCES department(id),
    CONSTRAINT fk_workforce_config_updater FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_eligible_role (
    task_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (task_id, role_code),
    CONSTRAINT fk_task_eligible_role_task FOREIGN KEY (task_id) REFERENCES hotel_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE attendance_correction_request
    MODIFY approver_user_id_snapshot BIGINT NULL,
    ADD COLUMN approval_route VARCHAR(40) NULL AFTER approver_user_id_snapshot;

ALTER TABLE employee_leave_request
    ADD COLUMN approval_route VARCHAR(40) NULL AFTER approver_user_id_snapshot;

