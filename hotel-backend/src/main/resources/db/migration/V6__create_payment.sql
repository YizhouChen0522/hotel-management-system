CREATE TABLE `payment` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,

                           `folio_id` BIGINT NOT NULL,

                           `amount` DECIMAL(12,2) NOT NULL,

                           `payment_method` VARCHAR(30) NOT NULL,

                           `status` VARCHAR(30) NOT NULL,

                           `reference_no` VARCHAR(100) DEFAULT NULL,

                           `note` VARCHAR(255) DEFAULT NULL,

                           `created_by` BIGINT DEFAULT NULL,

                           `paid_time` DATETIME DEFAULT NULL,

                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           `update_time` DATETIME NOT NULL
                               DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,

                           PRIMARY KEY (`id`),

                           KEY `idx_payment_folio`
                               (`folio_id`),

                           KEY `idx_payment_status`
                               (`status`),

                           KEY `idx_payment_paid_time`
                               (`paid_time`),

                           KEY `idx_payment_reference_no`
                               (`reference_no`),

                           CONSTRAINT `fk_payment_folio`
                               FOREIGN KEY (`folio_id`)
                                   REFERENCES `folio` (`id`),

                           CONSTRAINT `fk_payment_created_by`
                               FOREIGN KEY (`created_by`)
                                   REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;