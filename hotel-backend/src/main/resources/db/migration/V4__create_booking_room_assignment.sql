CREATE TABLE `booking_room_assignment` (
                                           `id` BIGINT NOT NULL AUTO_INCREMENT,

                                           `booking_id` BIGINT NOT NULL,

                                           `room_id` BIGINT NOT NULL,

                                           `room_type_id` BIGINT NOT NULL,

                                           `assignment_type` VARCHAR(30) NOT NULL,

                                           `start_time` DATETIME NOT NULL,

                                           `end_time` DATETIME DEFAULT NULL,

                                           `change_reason` VARCHAR(255) DEFAULT NULL,

                                           `created_by` BIGINT DEFAULT NULL,

                                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           PRIMARY KEY (`id`),

                                           KEY `idx_assignment_booking`
                                               (`booking_id`),

                                           KEY `idx_assignment_booking_end_time`
                                               (`booking_id`, `end_time`),

                                           KEY `idx_assignment_room`
                                               (`room_id`),

                                           CONSTRAINT `fk_assignment_booking`
                                               FOREIGN KEY (`booking_id`)
                                                   REFERENCES `booking` (`id`),

                                           CONSTRAINT `fk_assignment_room`
                                               FOREIGN KEY (`room_id`)
                                                   REFERENCES `room` (`id`),

                                           CONSTRAINT `fk_assignment_room_type`
                                               FOREIGN KEY (`room_type_id`)
                                                   REFERENCES `room_type` (`id`),

                                           CONSTRAINT `fk_assignment_created_by`
                                               FOREIGN KEY (`created_by`)
                                                   REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;