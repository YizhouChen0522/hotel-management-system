ALTER TABLE `payment`
    ADD COLUMN `request_key` VARCHAR(64) DEFAULT NULL
    COMMENT 'Idempotency key for payment recording'
        AFTER `reference_no`,

    ADD UNIQUE KEY `uk_payment_folio_request_key`
        (`folio_id`, `request_key`);