package com.johnny.hotel.inspection;import org.apache.ibatis.annotations.*;import java.util.*;
@Mapper public interface InspectionMapper{
 @Insert("INSERT INTO housekeeping_inspection(room_id,stay_id,assignment_id,turnover_task_id,cleaning_record_id,status) VALUES(#{roomId},#{stayId},#{assignmentId},#{turnoverTaskId},#{cleaningRecordId},0)")@Options(useGeneratedKeys=true,keyProperty="id")int insert(RoomInspection i);
 @Select("SELECT * FROM housekeeping_inspection WHERE id=#{id}")RoomInspection find(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE id=#{id} FOR UPDATE")RoomInspection lock(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE cleaning_record_id=#{id} FOR UPDATE")RoomInspection byCleaning(Long id);
 @Select("SELECT * FROM housekeeping_inspection WHERE room_id=#{room} ORDER BY id DESC")List<RoomInspection> byRoom(Long room);
 @Update("UPDATE housekeeping_inspection SET status=#{status},inspector_id=#{actor},failure_reason=#{reason},notes=#{notes},inspected_time=CURRENT_TIMESTAMP(6),followup_task_id=#{task} WHERE id=#{id} AND status=0")int decide(@Param("id")Long id,@Param("status")int status,@Param("actor")Long actor,@Param("reason")Integer reason,@Param("notes")String notes,@Param("task")Long task);
 @Select("SELECT status FROM housekeeping_inspection WHERE room_id=#{room} ORDER BY id DESC LIMIT 1")Integer latestStatus(Long room);
 @Select("SELECT EXISTS(SELECT 1 FROM room_turnover_task t WHERE t.room_id=#{room} AND t.id=(SELECT MAX(t2.id) FROM room_turnover_task t2 WHERE t2.room_id=#{room}) AND COALESCE((SELECT i.status FROM housekeeping_inspection i WHERE i.turnover_task_id=t.id ORDER BY i.id DESC LIMIT 1),0)<>1)")
 boolean hasUninspectedTurnover(Long room);
 @Insert("INSERT INTO rework_cleaning_request(task_id,inspection_id,room_id,stay_id,assignment_id,requested_by) VALUES(#{taskId},#{inspectionId},#{roomId},#{stayId},#{assignmentId},#{requestedBy})")int insertRework(ReworkCleaningRequest r);
 @Select("SELECT * FROM rework_cleaning_request WHERE task_id=#{task} FOR UPDATE")ReworkCleaningRequest reworkByTask(Long task);
 @Select("SELECT EXISTS(SELECT 1 FROM room_repair_task r JOIN hotel_task t ON t.id=r.task_id WHERE r.room_id=#{room} AND t.status NOT IN(2,3))")boolean hasOpenRepair(Long room);
 @Insert("INSERT INTO housekeeping_inspection(room_id,stay_id,assignment_id,turnover_task_id,repair_order_id,status) VALUES(#{roomId},#{stayId},#{assignmentId},#{turnoverTaskId},#{repairOrderId},0)")@Options(useGeneratedKeys=true,keyProperty="id")int insertForRepair(RoomInspection i);
 @Select("SELECT * FROM housekeeping_inspection WHERE repair_order_id=#{id} FOR UPDATE")RoomInspection byRepair(Long id);
 @Select("<script>SELECT * FROM housekeeping_inspection <where><if test='room!=null'>room_id=#{room}</if><if test='booking!=null'> AND stay_id=#{booking}</if><if test='employee!=null'> AND inspector_id=#{employee}</if><if test='status!=null'> AND status=#{status}</if></where> ORDER BY COALESCE(inspected_time,create_time) DESC,id DESC LIMIT #{offset},#{size}</script>")List<RoomInspection> page(@Param("room")Long room,@Param("booking")Long booking,@Param("employee")Long employee,@Param("status")Integer status,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM housekeeping_inspection <where><if test='room!=null'>room_id=#{room}</if><if test='booking!=null'> AND stay_id=#{booking}</if><if test='employee!=null'> AND inspector_id=#{employee}</if><if test='status!=null'> AND status=#{status}</if></where></script>")long count(@Param("room")Long room,@Param("booking")Long booking,@Param("employee")Long employee,@Param("status")Integer status);
}
