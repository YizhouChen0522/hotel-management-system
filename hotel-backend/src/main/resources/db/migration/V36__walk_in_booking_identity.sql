ALTER TABLE booking
 ADD COLUMN booker_guest_profile_id BIGINT NULL AFTER user_id,
 ADD COLUMN created_by_user_id BIGINT NULL AFTER booker_guest_profile_id,
 ADD COLUMN walk_in_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER reservation_source,
 ADD UNIQUE KEY uk_booking_walk_in_request(walk_in_request_key),
 ADD KEY idx_booking_booker_guest(booker_guest_profile_id),
 ADD KEY idx_booking_created_by(created_by_user_id),
 ADD CONSTRAINT fk_booking_booker_guest FOREIGN KEY(booker_guest_profile_id) REFERENCES guest_profile(id),
 ADD CONSTRAINT fk_booking_created_by FOREIGN KEY(created_by_user_id) REFERENCES sys_user(id);

CREATE TABLE walk_in_request_lock (
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE booking SET reservation_source='CUSTOMER_PORTAL' WHERE reservation_source='DIRECT';
UPDATE booking b JOIN guest_profile g ON g.linked_user_id=b.user_id
 SET b.booker_guest_profile_id=g.id WHERE b.booker_guest_profile_id IS NULL;
UPDATE booking SET created_by_user_id=user_id WHERE created_by_user_id IS NULL AND user_id IS NOT NULL;

DROP TRIGGER reservation_insert_guard;
DELIMITER $$
CREATE TRIGGER reservation_insert_guard BEFORE INSERT ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5)
    OR NEW.reservation_source NOT IN ('CUSTOMER_PORTAL','WALK_IN')
    OR (NEW.reservation_source='WALK_IN' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NULL OR NEW.status<>0))
    OR (NEW.reservation_source='CUSTOMER_PORTAL' AND (NEW.user_id IS NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NOT NULL)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation identity';
 END IF;
END$$
DELIMITER ;
