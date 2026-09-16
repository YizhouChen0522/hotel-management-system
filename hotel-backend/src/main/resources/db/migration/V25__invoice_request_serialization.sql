CREATE TABLE invoice_request_lock (
 request_key VARCHAR(100) NOT NULL PRIMARY KEY,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO invoice_request_lock(request_key) SELECT request_key FROM invoice;
