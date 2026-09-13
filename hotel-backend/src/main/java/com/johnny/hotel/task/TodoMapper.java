package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface TodoMapper {
 @Insert("INSERT INTO todo(task_id,assignment_id,user_id,status,is_active) VALUES(#{taskId},#{assignmentId},#{userId},0,1)") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Todo t);
 @Select("SELECT * FROM todo WHERE id=#{id} AND user_id=#{user}") Todo owned(@Param("id")Long id,@Param("user")Long user);
 @Select("SELECT * FROM todo WHERE task_id=#{task} AND user_id=#{user} AND is_active=1") Todo current(@Param("task")Long task,@Param("user")Long user);
 @Select("SELECT * FROM todo WHERE user_id=#{user} AND is_active=1 AND status<>2 ORDER BY id DESC LIMIT #{offset},#{size}") List<Todo> mine(@Param("user")Long user,@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM todo WHERE task_id=#{task} AND is_active=1 ORDER BY id FOR UPDATE") List<Todo> active(Long task);
 @Update("UPDATE todo SET status=#{next},acknowledged_time=CASE WHEN #{expected}=0 AND #{next}=1 THEN #{now} ELSE acknowledged_time END,completed_time=CASE WHEN #{next}=2 THEN #{now} ELSE completed_time END WHERE id=#{id} AND is_active=1 AND status=#{expected}") int transition(@Param("id")Long id,@Param("expected")int expected,@Param("next")int next,@Param("now")LocalDateTime now);
 @Update("UPDATE todo SET is_active=0,ended_time=#{now} WHERE task_id=#{task} AND is_active=1") int retire(@Param("task")Long task,@Param("now")LocalDateTime now);
}
