package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface RoomTurnoverTaskMapper {
    @Select("SELECT * FROM booking_room_assignment WHERE id=#{id}")
    BookingRoomAssignment assignmentIdentity(Long id);
    @Select("SELECT t.id,t.task_id,t.room_id,t.booking_id,t.assignment_id,h.status,(SELECT a.assignee_user_id FROM task_assignment a WHERE a.task_id=t.task_id AND a.is_current=1 ORDER BY a.id LIMIT 1) accepted_by,(SELECT a.accepted_time FROM task_assignment a WHERE a.task_id=t.task_id AND a.is_current=1 ORDER BY a.id LIMIT 1) accepted_time,h.completed_time,(SELECT r.actor_user_id FROM task_record r WHERE r.task_id=t.task_id AND r.record_type IN (4,5) ORDER BY r.id DESC LIMIT 1) completed_by,COALESCE((SELECT r.detail FROM task_record r WHERE r.task_id=t.task_id AND r.record_type IN (4,5) ORDER BY r.id DESC LIMIT 1),t.note) note,t.create_time,t.update_time FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id WHERE t.assignment_id=#{id} FOR UPDATE")
    RoomTurnoverTask byAssignment(Long id);
    @Select("SELECT t.id,t.task_id,t.room_id,t.booking_id,t.assignment_id,h.status,(SELECT a.assignee_user_id FROM task_assignment a WHERE a.task_id=t.task_id AND a.is_current=1 ORDER BY a.id LIMIT 1) accepted_by,(SELECT a.accepted_time FROM task_assignment a WHERE a.task_id=t.task_id AND a.is_current=1 ORDER BY a.id LIMIT 1) accepted_time,h.completed_time,(SELECT r.actor_user_id FROM task_record r WHERE r.task_id=t.task_id AND r.record_type IN (4,5) ORDER BY r.id DESC LIMIT 1) completed_by,COALESCE((SELECT r.detail FROM task_record r WHERE r.task_id=t.task_id AND r.record_type IN (4,5) ORDER BY r.id DESC LIMIT 1),t.note) note,t.create_time,t.update_time FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id WHERE t.id=#{id}")
    RoomTurnoverTask find(Long id);
    @Select("SELECT t.id,t.task_id,t.room_id,t.booking_id,t.assignment_id,h.status,t.note,t.create_time,t.update_time FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id WHERE t.id=#{id} FOR UPDATE")
    RoomTurnoverTask lock(Long id);
    @Select("""
        <script>SELECT t.id,t.task_id,t.room_id,t.booking_id,t.assignment_id,h.status,t.note,t.create_time,t.update_time FROM room_turnover_task t JOIN hotel_task h ON h.id=t.task_id
        <where><if test="status != null">h.status=#{status}</if><if test="roomId != null"> AND t.room_id=#{roomId}</if></where>
        ORDER BY t.id DESC LIMIT #{offset},#{size}</script>
        """)
    List<RoomTurnoverTask> page(@Param("status") Integer status,@Param("roomId") Long roomId,@Param("offset") int offset,@Param("size") int size);
    @Insert("INSERT INTO room_turnover_task(task_id,room_id,booking_id,assignment_id) VALUES(#{taskId},#{roomId},#{bookingId},#{assignmentId})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(RoomTurnoverTask task);
}
