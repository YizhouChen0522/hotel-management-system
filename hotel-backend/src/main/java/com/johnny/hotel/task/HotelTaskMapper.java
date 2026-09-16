package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface HotelTaskMapper {
 @Insert("INSERT INTO hotel_task(task_type,status,assignment_mode,execution_type,parent_task_id,title,description,source_key,request_key,created_by) VALUES(#{taskType},#{status},#{assignmentMode},COALESCE(#{executionType},0),#{parentTaskId},#{title},#{description},#{sourceKey},#{requestKey},#{createdBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(HotelTask task);
 @Select("SELECT * FROM hotel_task WHERE id=#{id}") HotelTask find(Long id);
 @Select("SELECT * FROM hotel_task WHERE id=#{id} FOR UPDATE") HotelTask lock(Long id);
 @Select("SELECT * FROM hotel_task WHERE source_key=#{key} FOR UPDATE") HotelTask bySourceForUpdate(String key);
 @Select("SELECT * FROM hotel_task WHERE created_by=#{creator} AND request_key=#{key}") HotelTask byRequest(@Param("creator")Long creator,@Param("key")String key);
 @Select("<script>SELECT * FROM hotel_task WHERE task_type IN (0,1,4,5,6) <if test='status!=null'> AND status=#{status}</if><if test='type!=null'> AND task_type=#{type}</if> ORDER BY id DESC LIMIT #{offset},#{size}</script>") List<HotelTask> page(@Param("status")Integer status,@Param("type")Integer type,@Param("offset")int offset,@Param("size")int size);
 @Update("UPDATE hotel_task SET status=#{next},completed_time=CASE WHEN #{next}=2 THEN #{now} ELSE completed_time END,cancelled_time=CASE WHEN #{next}=3 THEN #{now} ELSE cancelled_time END WHERE id=#{id} AND status=#{expected}") int transition(@Param("id")Long id,@Param("expected")Integer expected,@Param("next")Integer next,@Param("now")LocalDateTime now);
 @Update("UPDATE hotel_task SET status=#{status},assignment_mode=#{mode},completed_time=NULL,cancelled_time=NULL WHERE id=#{id} AND status IN (0,1,4)") int resetAssignment(@Param("id")Long id,@Param("status")Integer status,@Param("mode")Integer mode);
 @Update("UPDATE hotel_task SET status=2,completed_time=#{now} WHERE id=#{id} AND status IN (0,1,4)") int forceComplete(@Param("id")Long id,@Param("now")LocalDateTime now);
 @Select("SELECT * FROM hotel_task WHERE parent_task_id=#{id} ORDER BY id FOR UPDATE") List<HotelTask> children(Long id);
 @Update("UPDATE hotel_task SET execution_type=1,assignment_mode=2 WHERE id=#{id} AND task_type=3 AND execution_type=0 AND status IN (0,1,4)") int promoteMaintenanceShared(Long id);
}
