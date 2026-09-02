CREATE TABLE `booking_price_version` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT,

                                         `booking_id` BIGINT NOT NULL,

                                         `version_no` INT NOT NULL,

                                         `change_type` VARCHAR(50) NOT NULL,

                                         `reason` VARCHAR(255) DEFAULT NULL,

                                         `is_active` TINYINT NOT NULL DEFAULT 1,

                                         `total_price` DECIMAL(10,2) NOT NULL,

                                         `currency` VARCHAR(3) NOT NULL DEFAULT 'CAD',

                                         `created_by` BIGINT DEFAULT NULL,

                                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                         PRIMARY KEY (`id`),

                                         UNIQUE KEY `uk_booking_price_version`
                                             (`booking_id`, `version_no`),

                                         KEY `idx_booking_price_version_active`
                                             (`booking_id`, `is_active`),

                                         CONSTRAINT `fk_booking_price_version_booking`
                                             FOREIGN KEY (`booking_id`)
                                                 REFERENCES `booking` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booking_nightly_rate` (
                                        `id` BIGINT NOT NULL AUTO_INCREMENT,

                                        `price_version_id` BIGINT NOT NULL,

                                        `booking_id` BIGINT NOT NULL,

                                        `stay_date` DATE NOT NULL,

                                        `room_type_id` BIGINT NOT NULL,

                                        `rate_amount` DECIMAL(10,2) NOT NULL,

                                        `rate_source` VARCHAR(30) NOT NULL,

                                        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        PRIMARY KEY (`id`),

                                        UNIQUE KEY `uk_booking_nightly_rate_version_date`
                                            (`price_version_id`, `stay_date`),

                                        KEY `idx_booking_nightly_rate_booking`
                                            (`booking_id`),

                                        KEY `idx_booking_nightly_rate_stay_date`
                                            (`stay_date`),

                                        CONSTRAINT `fk_booking_nightly_rate_version`
                                            FOREIGN KEY (`price_version_id`)
                                                REFERENCES `booking_price_version` (`id`),

                                        CONSTRAINT `fk_booking_nightly_rate_booking`
                                            FOREIGN KEY (`booking_id`)
                                                REFERENCES `booking` (`id`),

                                        CONSTRAINT `fk_booking_nightly_rate_room_type`
                                            FOREIGN KEY (`room_type_id`)
                                                REFERENCES `room_type` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;