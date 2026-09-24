ALTER TABLE hotel_task
    ADD COLUMN target_role VARCHAR(32) NULL AFTER task_type,
    ADD INDEX idx_hotel_task_target_pool (target_role, status, assignment_mode, id);
