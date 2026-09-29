DROP TRIGGER reservation_insert_guard;
DELIMITER $$
CREATE TRIGGER reservation_insert_guard BEFORE INSERT ON booking FOR EACH ROW
BEGIN
 IF NEW.status NOT IN (0,1,4,5,6)
    OR NEW.reservation_source NOT IN ('CUSTOMER_PORTAL','WALK_IN','STAFF_DIRECT','GROUP')
    OR (NEW.reservation_source='WALK_IN' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NULL OR NEW.staff_direct_request_key IS NOT NULL OR NEW.status<>0))
    OR (NEW.reservation_source='STAFF_DIRECT' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.staff_direct_request_key IS NULL OR NEW.walk_in_request_key IS NOT NULL OR NEW.status<>0))
    OR (NEW.reservation_source='CUSTOMER_PORTAL' AND (NEW.user_id IS NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NOT NULL OR NEW.staff_direct_request_key IS NOT NULL))
    OR (NEW.reservation_source='GROUP' AND (NEW.user_id IS NOT NULL OR NEW.booker_guest_profile_id IS NULL
        OR NEW.created_by_user_id IS NULL OR NEW.walk_in_request_key IS NOT NULL OR NEW.staff_direct_request_key IS NOT NULL
        OR NEW.portal_request_key IS NOT NULL OR NEW.group_id IS NULL OR NEW.group_block_id IS NULL OR NEW.status<>0))
    OR (NEW.reservation_source<>'GROUP' AND (NEW.group_id IS NOT NULL OR NEW.group_block_id IS NOT NULL)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid reservation identity';
 END IF;
END$$
DELIMITER ;
