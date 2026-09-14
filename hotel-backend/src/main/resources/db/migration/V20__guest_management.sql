CREATE TABLE guest_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    linked_user_id BIGINT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(120) NULL,
    gender VARCHAR(20) NULL,
    date_of_birth DATE NULL,
    nationality VARCHAR(80) NULL,
    document_type VARCHAR(40) NULL,
    document_number VARCHAR(100) NULL,
    issuing_country VARCHAR(80) NULL,
    document_expiry_date DATE NULL,
    notes VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guest_profile_linked_user (linked_user_id),
    KEY idx_guest_profile_name (last_name, first_name),
    KEY idx_guest_profile_phone (phone),
    KEY idx_guest_profile_email (email),
    KEY idx_guest_profile_document (document_type, document_number),
    CONSTRAINT fk_guest_profile_user FOREIGN KEY (linked_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE booking_guest (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    guest_role TINYINT NOT NULL,
    primary_booking_id BIGINT GENERATED ALWAYS AS (CASE WHEN guest_role = 0 THEN booking_id ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_guest (booking_id, guest_id),
    UNIQUE KEY uk_booking_primary_guest (primary_booking_id),
    KEY idx_booking_guest_guest (guest_id),
    CONSTRAINT fk_booking_guest_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
    CONSTRAINT fk_booking_guest_profile FOREIGN KEY (guest_id) REFERENCES guest_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE guest_registration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    primary_guest_id BIGINT NOT NULL,
    registered_by BIGINT NOT NULL,
    registered_time DATETIME NOT NULL,
    registration_confirmed TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guest_registration_booking (booking_id),
    KEY idx_guest_registration_primary (primary_guest_id),
    CONSTRAINT fk_guest_registration_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
    CONSTRAINT fk_guest_registration_primary FOREIGN KEY (primary_guest_id) REFERENCES guest_profile(id),
    CONSTRAINT fk_guest_registration_operator FOREIGN KEY (registered_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
