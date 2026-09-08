package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.RoomBillingEvent;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RoomBillingEventMapper {
    @Select("SELECT * FROM room_billing_event WHERE booking_id=#{bookingId} ORDER BY id FOR UPDATE")
    List<RoomBillingEvent> selectByBookingIdForUpdate(Long bookingId);
    @Insert("INSERT INTO room_billing_event(booking_id,old_assignment_id,new_assignment_id,change_date,new_charges_total) VALUES(#{bookingId},#{oldAssignmentId},#{newAssignmentId},#{changeDate},#{newChargesTotal})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(RoomBillingEvent event);
}
