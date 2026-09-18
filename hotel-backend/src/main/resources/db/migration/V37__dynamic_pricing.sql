CREATE TABLE dynamic_pricing_control (id TINYINT NOT NULL PRIMARY KEY, update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6));
INSERT INTO dynamic_pricing_control(id) VALUES(1);

CREATE TABLE dynamic_pricing_policy (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 version_no INT NOT NULL,
 name VARCHAR(100) NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 active_slot TINYINT NULL,
 minimum_multiplier DECIMAL(8,4) NOT NULL,
 maximum_multiplier DECIMAL(8,4) NOT NULL,
 created_by BIGINT NOT NULL,
 activated_by BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 activated_time DATETIME(6) NULL,
 UNIQUE KEY uk_dynamic_policy_version(version_no),
 UNIQUE KEY uk_dynamic_policy_single_active(active_slot),
 CONSTRAINT fk_dynamic_policy_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_dynamic_policy_activator FOREIGN KEY(activated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dynamic_occupancy_band (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, policy_id BIGINT NOT NULL,
 label VARCHAR(50) NOT NULL, lower_inclusive INT NOT NULL, upper_exclusive INT NOT NULL, sort_order INT NOT NULL,
 UNIQUE KEY uk_dynamic_occupancy_order(policy_id,sort_order),
 CONSTRAINT fk_dynamic_occupancy_policy FOREIGN KEY(policy_id) REFERENCES dynamic_pricing_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE dynamic_booking_window_band (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, policy_id BIGINT NOT NULL,
 label VARCHAR(50) NOT NULL, min_days INT NOT NULL, max_days INT NULL, sort_order INT NOT NULL,
 UNIQUE KEY uk_dynamic_window_order(policy_id,sort_order),
 CONSTRAINT fk_dynamic_window_policy FOREIGN KEY(policy_id) REFERENCES dynamic_pricing_policy(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE dynamic_pricing_cell (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, policy_id BIGINT NOT NULL,
 occupancy_band_id BIGINT NOT NULL, booking_window_band_id BIGINT NOT NULL,
 adjustment_percent DECIMAL(8,4) NOT NULL,
 UNIQUE KEY uk_dynamic_cell(policy_id,occupancy_band_id,booking_window_band_id),
 CONSTRAINT fk_dynamic_cell_policy FOREIGN KEY(policy_id) REFERENCES dynamic_pricing_policy(id),
 CONSTRAINT fk_dynamic_cell_occupancy FOREIGN KEY(occupancy_band_id) REFERENCES dynamic_occupancy_band(id),
 CONSTRAINT fk_dynamic_cell_window FOREIGN KEY(booking_window_band_id) REFERENCES dynamic_booking_window_band(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE manual_rate_override (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, room_type_id BIGINT NOT NULL, stay_date DATE NOT NULL,
 override_type VARCHAR(30) NOT NULL, override_value DECIMAL(10,4) NOT NULL,
 reason VARCHAR(500) NOT NULL, active_slot TINYINT NULL DEFAULT 1,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 cancelled_time DATETIME(6) NULL,
 UNIQUE KEY uk_manual_rate_active(room_type_id,stay_date,active_slot),
 KEY idx_manual_rate_date(stay_date,room_type_id),
 CONSTRAINT fk_manual_rate_room_type FOREIGN KEY(room_type_id) REFERENCES room_type(id),
 CONSTRAINT fk_manual_rate_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT fk_manual_rate_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE booking_nightly_rate
 ADD COLUMN dynamic_policy_id BIGINT NULL AFTER rate_source,
 ADD COLUMN manual_override_id BIGINT NULL AFTER dynamic_policy_id,
 ADD KEY idx_booking_nightly_policy(dynamic_policy_id),
 ADD KEY idx_booking_nightly_override(manual_override_id),
 ADD CONSTRAINT fk_booking_nightly_policy FOREIGN KEY(dynamic_policy_id) REFERENCES dynamic_pricing_policy(id),
 ADD CONSTRAINT fk_booking_nightly_override FOREIGN KEY(manual_override_id) REFERENCES manual_rate_override(id);

INSERT INTO dynamic_pricing_policy(version_no,name,status,active_slot,minimum_multiplier,maximum_multiplier,created_by)
SELECT 1,'Default Dynamic Pricing Template',0,NULL,0.8000,2.0000,u.id FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id
WHERE r.role_code IN ('SUPER_ADMIN','OWNER') ORDER BY FIELD(r.role_code,'SUPER_ADMIN','OWNER'),u.id LIMIT 1;
SET @policy_id=LAST_INSERT_ID();
INSERT INTO dynamic_occupancy_band(policy_id,label,lower_inclusive,upper_exclusive,sort_order) VALUES
(@policy_id,'0-39%',0,40,1),(@policy_id,'40-69%',40,70,2),(@policy_id,'70-84%',70,85,3),(@policy_id,'85-94%',85,95,4),(@policy_id,'95-100%',95,101,5);
INSERT INTO dynamic_booking_window_band(policy_id,label,min_days,max_days,sort_order) VALUES
(@policy_id,'0-7 days',0,7,1),(@policy_id,'8-14 days',8,14,2),(@policy_id,'15-30 days',15,30,3),(@policy_id,'31+ days',31,NULL,4);
INSERT INTO dynamic_pricing_cell(policy_id,occupancy_band_id,booking_window_band_id,adjustment_percent)
SELECT @policy_id,o.id,w.id,
 CASE o.sort_order
  WHEN 1 THEN CASE w.sort_order WHEN 1 THEN -15 WHEN 2 THEN -10 WHEN 3 THEN -5 ELSE 0 END
  WHEN 2 THEN CASE w.sort_order WHEN 1 THEN -5 ELSE 0 END
  WHEN 3 THEN CASE w.sort_order WHEN 1 THEN 20 WHEN 2 THEN 15 WHEN 3 THEN 15 ELSE 10 END
  WHEN 4 THEN CASE w.sort_order WHEN 1 THEN 30 WHEN 2 THEN 25 WHEN 3 THEN 25 ELSE 20 END
  ELSE CASE w.sort_order WHEN 1 THEN 40 WHEN 2 THEN 35 WHEN 3 THEN 35 ELSE 30 END END
FROM dynamic_occupancy_band o CROSS JOIN dynamic_booking_window_band w WHERE o.policy_id=@policy_id AND w.policy_id=@policy_id;
