DROP TRIGGER preserve_reservation_contract;
DELIMITER $$
CREATE TRIGGER preserve_reservation_contract BEFORE UPDATE ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5,6) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reservation status cannot describe occupancy';
 END IF;
 IF EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=OLD.id) AND
    (NOT(OLD.check_in_date<=>NEW.check_in_date) OR NOT(OLD.check_out_date<=>NEW.check_out_date)
     OR NOT(OLD.room_type_id<=>NEW.room_type_id) OR NOT(OLD.total_price<=>NEW.total_price)
     OR NOT(OLD.guest_count<=>NEW.guest_count) OR NOT(OLD.user_id<=>NEW.user_id)
     OR OLD.status<>NEW.status OR NOT(OLD.reserved_room_id<=>NEW.reserved_room_id)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Arrived reservation contract is immutable';
 END IF;
END$$
DELIMITER ;
