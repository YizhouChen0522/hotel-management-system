package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface TaskRecordMapper {
 @Insert("INSERT INTO task_record(task_id,assignment_id,record_type,actor_user_id,detail) VALUES(#{taskId},#{assignmentId},#{recordType},#{actorUserId},#{detail})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(TaskRecord r);
 @Select("SELECT * FROM task_record WHERE task_id=#{task} ORDER BY id") List<TaskRecord> byTask(Long task);
}
