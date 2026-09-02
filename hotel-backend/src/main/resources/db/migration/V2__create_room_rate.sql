CREATE TABLE `room_rate` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,

                             `room_type_id` BIGINT NOT NULL,

                             `rate_date` DATE NOT NULL,

                             `price` DECIMAL(10,2) NOT NULL,

                             `rate_source` VARCHAR(30) NOT NULL DEFAULT 'MANUAL',

                             `description` VARCHAR(255) DEFAULT NULL,

                             `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             `update_time` DATETIME NOT NULL
                                                                DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

                             PRIMARY KEY (`id`),

                             UNIQUE KEY `uk_room_rate_type_date`
                                 (`room_type_id`, `rate_date`),

                             KEY `idx_room_rate_date`
                                 (`rate_date`),

                             KEY `idx_room_rate_room_type`
                                 (`room_type_id`),

                             CONSTRAINT `fk_room_rate_room_type`
                                 FOREIGN KEY (`room_type_id`)
                                     REFERENCES `room_type` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;