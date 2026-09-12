package com.johnny.hotel.task;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface TaskAssignmentMapper {
 @Insert("INSERT INTO task_assignment(task_id,assignee_user_id,status,assignment_round,is_current,assigned_by,accepted_time) VALUES(#{taskId},#{assigneeUserId},#{status},#{assignmentRound},#{isCurrent},#{assignedBy},#{acceptedTime})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(TaskAssignment a);
 @Select("SELECT * FROM task_assignment WHERE task_id=#{task} ORDER BY id") List<TaskAssignment> byTask(Long task);
 @Select("SELECT * FROM task_assignment WHERE task_id=#{task} AND assignee_user_id=#{user} AND is_current=1 ORDER BY id DESC LIMIT 1 FOR UPDATE") TaskAssignment currentForUser(@Param("task")Long task,@Param("user")Long user);
 @Select("SELECT * FROM task_assignment WHERE assignee_user_id=#{user} AND is_current=1 AND status != 2 ORDER BY id DESC") List<TaskAssignment> todo(Long user);
 @Select("SELECT COUNT(*) FROM task_assignment WHERE task_id=#{task} AND is_current=1") int currentCount(Long task);
 @Select("SELECT COUNT(*) FROM task_assignment WHERE task_id=#{task} AND is_current=1 AND status != 2") int incompleteCount(Long task);
 @Select("SELECT COALESCE(MAX(assignment_round),0) FROM task_assignment WHERE task_id=#{task}") int maxRound(Long task);
 @Update("UPDATE task_assignment SET status=1,accepted_time=#{now} WHERE id=#{id} AND status=0 AND is_current=1") int accept(@Param("id")Long id,@Param("now")LocalDateTime now);
 @Update("UPDATE task_assignment SET status=2,completed_time=#{now} WHERE id=#{id} AND status=1 AND is_current=1") int complete(@Param("id")Long id,@Param("now")LocalDateTime now);
 @Update("UPDATE task_assignment SET is_current=0,ended_time=#{now} WHERE task_id=#{task} AND is_current=1") int endCurrent(@Param("task")Long task,@Param("now")LocalDateTime now);
 @Update("UPDATE task_assignment SET status=2,completed_time=#{now} WHERE task_id=#{task} AND is_current=1 AND status != 2") int forceComplete(@Param("task")Long task,@Param("now")LocalDateTime now);
}
