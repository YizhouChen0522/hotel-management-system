package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.RoomBillingEvent;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RoomBillingEventMapper {
    @Select("SELECT * FROM room_billing_event WHERE stay_id=#{stayId} ORDER BY id FOR UPDATE")
    List<RoomBillingEvent> selectByStayIdForUpdate(Long stayId);
    @Insert("INSERT INTO room_billing_event(stay_id,old_assignment_id,new_assignment_id,change_date,new_charges_total) VALUES(#{stayId},#{oldAssignmentId},#{newAssignmentId},#{changeDate},#{newChargesTotal})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(RoomBillingEvent event);
}
