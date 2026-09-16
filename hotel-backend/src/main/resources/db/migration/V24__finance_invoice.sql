INSERT INTO sys_role(role_code,role_name,description)
SELECT 'FINANCE','Finance','Hotel finance and invoice operations'
WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE role_code='FINANCE');

CREATE TABLE invoice_number_sequence (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoice (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 invoice_number VARCHAR(40) NOT NULL,
 request_key VARCHAR(100) NOT NULL,
 folio_id BIGINT NOT NULL,
 booking_id BIGINT NOT NULL,
 currency VARCHAR(10) NOT NULL,
 recipient_name VARCHAR(120) NOT NULL,
 recipient_email VARCHAR(120) NULL,
 billing_address VARCHAR(500) NULL,
 tax_identifier VARCHAR(80) NULL,
 status TINYINT NOT NULL DEFAULT 0,
 subtotal DECIMAL(12,2) NOT NULL,
 total_amount DECIMAL(12,2) NOT NULL,
 issued_by BIGINT NOT NULL,
 issued_time DATETIME(6) NOT NULL,
 voided_by BIGINT NULL,
 voided_time DATETIME(6) NULL,
 void_reason VARCHAR(500) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_invoice_number(invoice_number),
 UNIQUE KEY uk_invoice_request(request_key),
 KEY idx_invoice_folio(folio_id,status), KEY idx_invoice_booking(booking_id,status),
 CONSTRAINT fk_invoice_folio FOREIGN KEY(folio_id) REFERENCES folio(id),
 CONSTRAINT fk_invoice_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
 CONSTRAINT fk_invoice_issuer FOREIGN KEY(issued_by) REFERENCES sys_user(id),
 CONSTRAINT fk_invoice_voider FOREIGN KEY(voided_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoice_line (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 invoice_id BIGINT NOT NULL,
 line_number INT NOT NULL,
 folio_item_id BIGINT NOT NULL,
 item_type VARCHAR(50) NOT NULL,
 description VARCHAR(255) NOT NULL,
 business_date DATE NOT NULL,
 quantity DECIMAL(12,2) NOT NULL,
 unit_price DECIMAL(12,2) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_invoice_line_number(invoice_id,line_number),
 UNIQUE KEY uk_invoice_folio_item(invoice_id,folio_item_id),
 CONSTRAINT fk_invoice_line_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_invoice_line_folio_item FOREIGN KEY(folio_item_id) REFERENCES folio_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
