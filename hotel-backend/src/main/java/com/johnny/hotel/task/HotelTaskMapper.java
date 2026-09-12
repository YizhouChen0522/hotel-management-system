package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface HotelTaskMapper {
 @Insert("INSERT INTO hotel_task(task_type,status,assignment_mode,title,description,source_key,request_key,created_by) VALUES(#{taskType},#{status},#{assignmentMode},#{title},#{description},#{sourceKey},#{requestKey},#{createdBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(HotelTask task);
 @Select("SELECT * FROM hotel_task WHERE id=#{id}") HotelTask find(Long id);
 @Select("SELECT * FROM hotel_task WHERE id=#{id} FOR UPDATE") HotelTask lock(Long id);
 @Select("SELECT * FROM hotel_task WHERE source_key=#{key} FOR UPDATE") HotelTask bySourceForUpdate(String key);
 @Select("SELECT * FROM hotel_task WHERE created_by=#{creator} AND request_key=#{key}") HotelTask byRequest(@Param("creator")Long creator,@Param("key")String key);
 @Select("<script>SELECT * FROM hotel_task <where><if test='status!=null'>status=#{status}</if><if test='type!=null'> AND task_type=#{type}</if></where> ORDER BY id DESC LIMIT #{offset},#{size}</script>") List<HotelTask> page(@Param("status")Integer status,@Param("type")Integer type,@Param("offset")int offset,@Param("size")int size);
 @Update("UPDATE hotel_task SET status=#{next},completed_time=CASE WHEN #{next}=2 THEN #{now} ELSE completed_time END,cancelled_time=CASE WHEN #{next}=3 THEN #{now} ELSE cancelled_time END WHERE id=#{id} AND status=#{expected}") int transition(@Param("id")Long id,@Param("expected")Integer expected,@Param("next")Integer next,@Param("now")LocalDateTime now);
 @Update("UPDATE hotel_task SET status=#{status},assignment_mode=#{mode},completed_time=NULL,cancelled_time=NULL WHERE id=#{id} AND status IN (0,1)") int resetAssignment(@Param("id")Long id,@Param("status")Integer status,@Param("mode")Integer mode);
 @Update("UPDATE hotel_task SET status=2,completed_time=#{now} WHERE id=#{id} AND status IN (0,1)") int forceComplete(@Param("id")Long id,@Param("now")LocalDateTime now);
}
