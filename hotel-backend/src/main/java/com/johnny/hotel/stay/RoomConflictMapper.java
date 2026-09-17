package com.johnny.hotel.stay;
import com.johnny.hotel.entity.Booking;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
@Mapper
public interface RoomConflictMapper {
    @Select("""
        SELECT b.* FROM booking b LEFT JOIN stay s ON s.booking_id=b.id
        LEFT JOIN stay_room_assignment segment ON segment.stay_id=s.id AND segment.end_time IS NULL
        WHERE b.id<>#{excluded} AND b.status=1
        AND ((s.id IS NULL AND b.reserved_room_id=#{roomId}) OR (s.status=1 AND segment.room_id=#{roomId}))
        AND b.check_in_date < #{end}
        AND COALESCE((SELECT a.new_end FROM stay_adjustment a WHERE a.stay_id=s.id ORDER BY a.id DESC LIMIT 1),b.check_out_date)>#{start}
        ORDER BY b.check_in_date,b.id
        """)
    List<Booking> overlapping(@Param("roomId") Long roomId,@Param("excluded") Long excluded,@Param("start") LocalDate start,@Param("end") LocalDate end);
    @Select("SELECT b.* FROM booking b WHERE b.reserved_room_id=#{roomId} AND b.id<>#{excluded} AND b.status=1 AND b.check_in_date=#{date} AND NOT EXISTS(SELECT 1 FROM stay s WHERE s.booking_id=b.id) ORDER BY b.id")
    List<Booking> arriving(@Param("roomId") Long roomId,@Param("excluded") Long excluded,@Param("date") LocalDate date);
}
