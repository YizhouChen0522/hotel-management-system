CREATE TABLE `sys_user` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
                            `username` VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Login Username',
                            `password` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Encrypted Password',
                            `real_name` VARCHAR(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Real Name',
                            `phone` VARCHAR(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Phone Number',
                            `email` VARCHAR(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email Address',
                            `status` TINYINT DEFAULT 1 COMMENT 'Status: 0 Disabled, 1 Active, 2 Pending, 3 Rejected',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
                            `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update Time',
                            `apply_role_code` VARCHAR(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Applied Role Code',
                            `apply_reason` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Application Reason',
                            `approved_by` BIGINT DEFAULT NULL COMMENT 'Approved By User ID',
                            `approved_time` DATETIME DEFAULT NULL COMMENT 'Approval Time',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_sys_user_username` (`username`),
                            UNIQUE KEY `uk_sys_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `sys_role` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
                            `role_name` VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Role Name',
                            `role_code` VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Role Code',
                            `description` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Role Description',
                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_sys_role_name` (`role_name`),
                            UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `sys_user_role` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
                                 `user_id` BIGINT NOT NULL COMMENT 'User ID',
                                 `role_id` BIGINT NOT NULL COMMENT 'Role ID',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
                                 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `sys_audit_log` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `operator_id` BIGINT NOT NULL,
                                 `target_user_id` BIGINT DEFAULT NULL,
                                 `action` VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                 `detail` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `room_type` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                             `type_name` VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                             `description` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                             `base_price` DECIMAL(10,2) NOT NULL,
                             `capacity` INT NOT NULL,
                             `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0 Disabled, 1 Enabled',
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `room` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                        `room_number` VARCHAR(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                        `room_type_id` BIGINT NOT NULL,
                        `floor` INT NOT NULL,
                        `status` TINYINT NOT NULL DEFAULT 1
                            COMMENT '0 Disabled, 1 Available, 2 Booked, 3 Maintenance, 4 Occupied',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_room_number` (`room_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booking` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `user_id` BIGINT NOT NULL,
                           `room_type_id` BIGINT NOT NULL,
                           `assigned_room_id` BIGINT DEFAULT NULL,
                           `guest_count` INT NOT NULL,
                           `check_in_date` DATE NOT NULL,
                           `check_out_date` DATE NOT NULL,
                           `status` TINYINT NOT NULL DEFAULT 0
                               COMMENT '0 Pending, 1 Approved, 2 Checked In, 3 Checked Out, 4 Cancelled, 5 Rejected',
                           `total_price` DECIMAL(10,2) DEFAULT NULL,
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


INSERT INTO `sys_role`
(`role_code`, `role_name`, `description`)
VALUES
    ('CUSTOMER', 'Customer', 'Default role for hotel customers'),
    ('STAFF', 'Staff', 'Basic hotel staff role'),
    ('HR_ADMIN', 'HR Admin', 'Human resources administrator'),
    ('MANAGER', 'Manager', 'Hotel manager'),
    ('OWNER', 'Owner', 'Hotel owner'),
    ('SUPER_ADMIN', 'Super Admin', 'System administrator');