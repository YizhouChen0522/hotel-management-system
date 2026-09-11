package com.johnny.hotel.stay;
import com.johnny.hotel.entity.Booking;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
@Mapper
public interface RoomConflictMapper {
    @Select("""
        SELECT b.* FROM booking b WHERE b.assigned_room_id=#{roomId} AND b.id<>#{excluded} AND b.status IN (1,2)
        AND b.check_in_date < #{end}
        AND COALESCE((SELECT a.new_end FROM stay_adjustment a WHERE a.booking_id=b.id ORDER BY a.id DESC LIMIT 1),b.check_out_date)>#{start}
        ORDER BY b.check_in_date,b.id
        """)
    List<Booking> overlapping(@Param("roomId") Long roomId,@Param("excluded") Long excluded,@Param("start") LocalDate start,@Param("end") LocalDate end);
    @Select("SELECT * FROM booking WHERE assigned_room_id=#{roomId} AND id<>#{excluded} AND status IN (1,2) AND check_in_date=#{date} ORDER BY id")
    List<Booking> arriving(@Param("roomId") Long roomId,@Param("excluded") Long excluded,@Param("date") LocalDate date);
}
