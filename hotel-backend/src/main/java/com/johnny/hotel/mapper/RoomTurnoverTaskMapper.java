package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface RoomTurnoverTaskMapper {
    @Select("SELECT * FROM booking_room_assignment WHERE id=#{id}")
    BookingRoomAssignment assignmentIdentity(Long id);
    @Select("SELECT * FROM room_turnover_task WHERE assignment_id=#{id} FOR UPDATE")
    RoomTurnoverTask byAssignment(Long id);
    @Select("SELECT * FROM room_turnover_task WHERE id=#{id}")
    RoomTurnoverTask find(Long id);
    @Select("SELECT * FROM room_turnover_task WHERE id=#{id} FOR UPDATE")
    RoomTurnoverTask lock(Long id);
    @Select("""
        <script>SELECT * FROM room_turnover_task
        <where><if test="status != null"> status=#{status} </if><if test="roomId != null"> AND room_id=#{roomId} </if></where>
        ORDER BY id DESC LIMIT #{offset},#{size}</script>
        """)
    List<RoomTurnoverTask> page(@Param("status") Integer status,@Param("roomId") Long roomId,@Param("offset") int offset,@Param("size") int size);
    @Insert("INSERT INTO room_turnover_task(room_id,booking_id,assignment_id) VALUES(#{roomId},#{bookingId},#{assignmentId})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(RoomTurnoverTask task);
    @Update("UPDATE room_turnover_task SET status=1,accepted_by=#{actor},accepted_time=#{now} WHERE id=#{id} AND status=0")
    int accept(@Param("id") Long id,@Param("actor") Long actor,@Param("now") LocalDateTime now);
    @Update("UPDATE room_turnover_task SET status=2,completed_by=#{actor},completed_time=#{now},note=#{note} WHERE id=#{id} AND status=#{expected}")
    int complete(@Param("id") Long id,@Param("expected") Integer expected,@Param("actor") Long actor,@Param("now") LocalDateTime now,@Param("note") String note);
}
