DELIMITER $$
CREATE PROCEDURE add_history_index_if_missing(IN target_table VARCHAR(64), IN target_index VARCHAR(64), IN index_columns VARCHAR(255))
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=target_table AND index_name=target_index) THEN
  SET @ddl=CONCAT('CREATE INDEX `',target_index,'` ON `',target_table,'` (',index_columns,')');
  PREPARE stmt FROM @ddl;
  EXECUTE stmt;
  DEALLOCATE PREPARE stmt;
 END IF;
END$$
DELIMITER ;

CALL add_history_index_if_missing('cleaning_record','idx_cleaning_completed_id','`completed_time`,`id`');
CALL add_history_index_if_missing('housekeeping_inspection','idx_inspection_time_id','`inspected_time`,`id`');
CALL add_history_index_if_missing('task_record','idx_task_record_task_time_id','`task_id`,`create_time`,`id`');
CALL add_history_index_if_missing('organization_change_history','idx_org_history_time_id','`effective_time`,`id`');
CALL add_history_index_if_missing('stay_history','idx_stay_history_user_time_id','`user_id`,`actual_check_in_time`,`id`');

DROP PROCEDURE add_history_index_if_missing;
