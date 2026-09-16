CREATE TABLE charge_catalog (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(120) NOT NULL,
 charge_type TINYINT NOT NULL,
 category VARCHAR(80) NOT NULL,
 unit_price DECIMAL(12,2) NOT NULL,
 status TINYINT NOT NULL DEFAULT 1,
 description VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_charge_catalog_code(code),
 KEY idx_charge_catalog_sale(charge_type,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guest_service_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL, folio_id BIGINT NOT NULL, customer_user_id BIGINT NOT NULL,
 catalog_id BIGINT NOT NULL, task_id BIGINT NOT NULL,
 catalog_code_snapshot VARCHAR(50) NOT NULL, name_snapshot VARCHAR(120) NOT NULL,
 unit_price_snapshot DECIMAL(12,2) NOT NULL, quantity DECIMAL(10,2) NOT NULL, amount DECIMAL(12,2) NOT NULL,
 request_note VARCHAR(500) NULL, request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status TINYINT NOT NULL DEFAULT 0, folio_item_id BIGINT NULL, requested_by BIGINT NOT NULL,
 completed_by BIGINT NULL, completed_time DATETIME(6) NULL, cancelled_by BIGINT NULL, cancelled_time DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_service_order_request(requested_by,request_key), UNIQUE KEY uk_service_order_task(task_id), UNIQUE KEY uk_service_order_folio_item(folio_item_id),
 KEY idx_service_order_booking_time(booking_id,create_time,id), KEY idx_service_order_status_time(status,create_time,id),
 CONSTRAINT fk_service_order_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_service_order_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
 CONSTRAINT fk_service_order_customer FOREIGN KEY(customer_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_service_order_catalog FOREIGN KEY(catalog_id) REFERENCES charge_catalog(id),
 CONSTRAINT fk_service_order_task FOREIGN KEY(task_id) REFERENCES hotel_task(id),
 CONSTRAINT fk_service_order_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_service_order_completer FOREIGN KEY(completed_by) REFERENCES sys_user(id),
 CONSTRAINT fk_service_order_canceller FOREIGN KEY(cancelled_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guest_purchase (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 booking_id BIGINT NOT NULL, folio_id BIGINT NOT NULL, customer_user_id BIGINT NOT NULL,
 request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, status TINYINT NOT NULL DEFAULT 0,
 total_amount DECIMAL(12,2) NOT NULL, requested_by BIGINT NOT NULL, confirmed_by BIGINT NULL,
 confirmed_time DATETIME(6) NULL, cancelled_by BIGINT NULL, cancelled_time DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_guest_purchase_request(requested_by,request_key),
 KEY idx_guest_purchase_booking_time(booking_id,create_time,id), KEY idx_guest_purchase_status_time(status,create_time,id),
 CONSTRAINT fk_purchase_booking FOREIGN KEY(booking_id) REFERENCES booking(id), CONSTRAINT fk_purchase_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
 CONSTRAINT fk_purchase_customer FOREIGN KEY(customer_user_id) REFERENCES sys_user(id), CONSTRAINT fk_purchase_requester FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_purchase_confirmer FOREIGN KEY(confirmed_by) REFERENCES sys_user(id), CONSTRAINT fk_purchase_canceller FOREIGN KEY(cancelled_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guest_purchase_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, purchase_id BIGINT NOT NULL, catalog_id BIGINT NOT NULL,
 catalog_code_snapshot VARCHAR(50) NOT NULL, name_snapshot VARCHAR(120) NOT NULL,
 unit_price_snapshot DECIMAL(12,2) NOT NULL, quantity DECIMAL(10,2) NOT NULL, amount DECIMAL(12,2) NOT NULL,
 folio_item_id BIGINT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_purchase_catalog(purchase_id,catalog_id), UNIQUE KEY uk_purchase_item_folio(folio_item_id),
 CONSTRAINT fk_purchase_item_purchase FOREIGN KEY(purchase_id) REFERENCES guest_purchase(id),
 CONSTRAINT fk_purchase_item_catalog FOREIGN KEY(catalog_id) REFERENCES charge_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE folio_item ADD COLUMN source_type VARCHAR(40) NULL AFTER stay_adjustment_id,
 ADD COLUMN source_id BIGINT NULL AFTER source_type,
 ADD UNIQUE KEY uk_folio_item_business_source(source_type,source_id);
ALTER TABLE guest_service_order ADD CONSTRAINT fk_service_order_ledger FOREIGN KEY(folio_item_id) REFERENCES folio_item(id);
ALTER TABLE guest_purchase_item ADD CONSTRAINT fk_purchase_item_ledger FOREIGN KEY(folio_item_id) REFERENCES folio_item(id);
