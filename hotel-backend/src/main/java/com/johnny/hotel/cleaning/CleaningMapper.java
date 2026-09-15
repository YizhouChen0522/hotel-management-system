package com.johnny.hotel.cleaning;import org.apache.ibatis.annotations.*;import java.util.*;
@Mapper public interface CleaningMapper {
 @Insert("INSERT INTO stayover_cleaning_request(task_id,booking_id,assignment_id,room_id,requested_by,request_key,requested_time) VALUES(#{taskId},#{bookingId},#{assignmentId},#{roomId},#{requestedBy},#{requestKey},#{requestedTime})") @Options(useGeneratedKeys=true,keyProperty="id") int insertRequest(StayoverCleaningRequest r);
 @Select("SELECT * FROM stayover_cleaning_request WHERE requested_by=#{actor} AND request_key=#{key} FOR UPDATE") StayoverCleaningRequest requestByKey(@Param("actor")Long actor,@Param("key")String key);
 @Select("SELECT * FROM stayover_cleaning_request WHERE task_id=#{taskId} FOR UPDATE") StayoverCleaningRequest requestByTask(Long taskId);
 @Insert("INSERT INTO cleaning_record(room_id,booking_id,assignment_id,task_id,cleaning_type,requested_by,completed_by,requested_time,started_time,completed_time,notes) VALUES(#{roomId},#{bookingId},#{assignmentId},#{taskId},#{cleaningType},#{requestedBy},#{completedBy},#{requestedTime},#{startedTime},#{completedTime},#{notes})") @Options(useGeneratedKeys=true,keyProperty="id") int insertRecord(CleaningRecord r);
 @Select("SELECT * FROM cleaning_record WHERE task_id=#{taskId} FOR UPDATE") CleaningRecord byTask(Long taskId);
 @Select("SELECT * FROM cleaning_record WHERE id=#{id}") CleaningRecord find(Long id);
 @Select("SELECT * FROM cleaning_record WHERE room_id=#{roomId} ORDER BY completed_time DESC,id DESC") List<CleaningRecord> byRoom(Long roomId);
 @Select("SELECT * FROM cleaning_record WHERE booking_id=#{bookingId} ORDER BY completed_time DESC,id DESC") List<CleaningRecord> byBooking(Long bookingId);
 @Select("SELECT * FROM cleaning_record WHERE assignment_id=#{assignmentId} ORDER BY completed_time DESC,id DESC") List<CleaningRecord> byAssignment(Long assignmentId);
 @Select("SELECT * FROM cleaning_record WHERE booking_id=#{bookingId} AND cleaning_type=0 ORDER BY completed_time DESC,id DESC") List<CleaningRecord> stayoverByBooking(Long bookingId);
 @Select("SELECT MIN(acknowledged_time) FROM todo WHERE task_id=#{taskId} AND acknowledged_time IS NOT NULL") java.time.LocalDateTime started(Long taskId);
 @Select("SELECT * FROM rework_cleaning_request WHERE task_id=#{taskId} FOR UPDATE") com.johnny.hotel.inspection.ReworkCleaningRequest reworkByTask(Long taskId);
}
