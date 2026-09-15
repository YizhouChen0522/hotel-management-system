package com.johnny.hotel.inspection;import org.apache.ibatis.annotations.*;import java.util.*;
@Mapper public interface InspectionMapper{
 @Insert("INSERT INTO housekeeping_inspection(room_id,booking_id,assignment_id,turnover_task_id,cleaning_record_id,status) VALUES(#{roomId},#{bookingId},#{assignmentId},#{turnoverTaskId},#{cleaningRecordId},0)")@Options(useGeneratedKeys=true,keyProperty="id")int insert(RoomInspection i);
 @Select("SELECT * FROM housekeeping_inspection WHERE id=#{id}")RoomInspection find(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE id=#{id} FOR UPDATE")RoomInspection lock(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE cleaning_record_id=#{id} FOR UPDATE")RoomInspection byCleaning(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE room_id=#{room} ORDER BY id DESC")List<RoomInspection> byRoom(Long room);
 @Update("UPDATE housekeeping_inspection SET status=#{status},inspector_id=#{actor},failure_reason=#{reason},notes=#{notes},inspected_time=CURRENT_TIMESTAMP(6),followup_task_id=#{task} WHERE id=#{id} AND status=0")int decide(@Param("id")Long id,@Param("status")int status,@Param("actor")Long actor,@Param("reason")Integer reason,@Param("notes")String notes,@Param("task")Long task);
 @Select("SELECT status FROM housekeeping_inspection WHERE room_id=#{room} ORDER BY id DESC LIMIT 1")Integer latestStatus(Long room);
 @Insert("INSERT INTO rework_cleaning_request(task_id,inspection_id,room_id,booking_id,assignment_id,requested_by) VALUES(#{taskId},#{inspectionId},#{roomId},#{bookingId},#{assignmentId},#{requestedBy})")int insertRework(ReworkCleaningRequest r);
 @Select("SELECT * FROM rework_cleaning_request WHERE task_id=#{task} FOR UPDATE")ReworkCleaningRequest reworkByTask(Long task);
 @Insert("INSERT INTO room_repair_task(task_id,inspection_id,room_id) VALUES(#{task},#{inspection},#{room})")int insertRepair(@Param("task")Long task,@Param("inspection")Long inspection,@Param("room")Long room);
 @Select("SELECT EXISTS(SELECT 1 FROM room_repair_task r JOIN hotel_task t ON t.id=r.task_id WHERE r.room_id=#{room} AND t.status NOT IN(2,3))")boolean hasOpenRepair(Long room);
}
