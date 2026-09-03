CREATE TABLE `folio` (
                         `id` BIGINT NOT NULL AUTO_INCREMENT,

                         `booking_id` BIGINT NOT NULL,

                         `status` VARCHAR(30) NOT NULL DEFAULT 'OPEN',

                         `currency` VARCHAR(3) NOT NULL DEFAULT 'CAD',

                         `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,

                         `paid_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,

                         `balance_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,

                         `settled_time` DATETIME DEFAULT NULL,

                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         `update_time` DATETIME NOT NULL
                             DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (`id`),

                         UNIQUE KEY `uk_folio_booking`
                             (`booking_id`),

                         KEY `idx_folio_status`
                             (`status`),

                         CONSTRAINT `fk_folio_booking`
                             FOREIGN KEY (`booking_id`)
                                 REFERENCES `booking` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `folio_item` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,

                              `folio_id` BIGINT NOT NULL,

                              `item_type` VARCHAR(40) NOT NULL,

                              `description` VARCHAR(500) NOT NULL,

                              `business_date` DATE DEFAULT NULL,

                              `quantity` DECIMAL(10,2) DEFAULT NULL,

                              `unit_price` DECIMAL(12,2) DEFAULT NULL,

                              `amount` DECIMAL(12,2) NOT NULL,

                              `room_id` BIGINT DEFAULT NULL,

                              `room_type_id` BIGINT DEFAULT NULL,

                              `room_assignment_id` BIGINT DEFAULT NULL,

                              `source_item_id` BIGINT DEFAULT NULL,

                              `refundable` TINYINT NOT NULL DEFAULT 1,

                              `created_by` BIGINT DEFAULT NULL,

                              `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              PRIMARY KEY (`id`),

                              KEY `idx_folio_item_folio`
                                  (`folio_id`),

                              KEY `idx_folio_item_business_date`
                                  (`business_date`),

                              KEY `idx_folio_item_type`
                                  (`item_type`),

                              KEY `idx_folio_item_assignment`
                                  (`room_assignment_id`),

                              CONSTRAINT `fk_folio_item_folio`
                                  FOREIGN KEY (`folio_id`)
                                      REFERENCES `folio` (`id`),

                              CONSTRAINT `fk_folio_item_room`
                                  FOREIGN KEY (`room_id`)
                                      REFERENCES `room` (`id`),

                              CONSTRAINT `fk_folio_item_room_type`
                                  FOREIGN KEY (`room_type_id`)
                                      REFERENCES `room_type` (`id`),

                              CONSTRAINT `fk_folio_item_assignment`
                                  FOREIGN KEY (`room_assignment_id`)
                                      REFERENCES `booking_room_assignment` (`id`),

                              CONSTRAINT `fk_folio_item_source`
                                  FOREIGN KEY (`source_item_id`)
                                      REFERENCES `folio_item` (`id`),

                              CONSTRAINT `fk_folio_item_created_by`
                                  FOREIGN KEY (`created_by`)
                                      REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;