package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface TaskRecordMapper {
 @Insert("INSERT INTO task_record(task_id,assignment_id,record_type,actor_user_id,detail) VALUES(#{taskId},#{assignmentId},#{recordType},#{actorUserId},#{detail})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(TaskRecord r);
 @Select("SELECT * FROM task_record WHERE task_id=#{task} ORDER BY id") List<TaskRecord> byTask(Long task);
 @Select("<script>SELECT * FROM task_record WHERE task_id=#{task}<if test='type!=null'> AND record_type=#{type}</if><if test='actor!=null'> AND actor_user_id=#{actor}</if> ORDER BY create_time DESC,id DESC LIMIT #{offset},#{size}</script>")List<TaskRecord> page(@Param("task")Long task,@Param("type")Integer type,@Param("actor")Long actor,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM task_record WHERE task_id=#{task}<if test='type!=null'> AND record_type=#{type}</if><if test='actor!=null'> AND actor_user_id=#{actor}</if></script>")long count(@Param("task")Long task,@Param("type")Integer type,@Param("actor")Long actor);
}
