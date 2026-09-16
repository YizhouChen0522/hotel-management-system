package com.johnny.hotel.organization;
import com.johnny.hotel.entity.SysUser;
import org.apache.ibatis.annotations.*;import java.util.List;
@Mapper public interface OrganizationMapper {
 @Select("SELECT id FROM organization_guard WHERE id=1 FOR UPDATE") Integer guard();
 @Select("SELECT * FROM department WHERE id=#{id}") Department department(Long id);
 @Select("SELECT * FROM department WHERE name=#{name}") Department named(String name);
 @Select("SELECT * FROM department ORDER BY id LIMIT #{offset},#{size}") List<Department> departments(@Param("offset")int offset,@Param("size")int size);
 @Select("SELECT * FROM department WHERE manager_user_id=#{id} FOR UPDATE") Department managed(Long id);
 @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE") SysUser user(Long id);
 @Select("SELECT COUNT(*) FROM sys_user WHERE department_id=#{id}") int memberCount(Long id);
 @Insert("INSERT INTO department(name) VALUES(#{name})") @Options(useGeneratedKeys=true,keyProperty="id") int insertDepartment(Department d);
 @Update("UPDATE department SET manager_user_id=#{manager},version=version+1 WHERE id=#{id} AND version=#{version}") int manager(@Param("id")Long id,@Param("manager")Long manager,@Param("version")Long version);
 @Update("UPDATE department SET status=0,version=version+1 WHERE id=#{id} AND version=#{version} AND manager_user_id IS NULL") int disable(@Param("id")Long id,@Param("version")Long version);
 @Update("UPDATE sys_user SET department_id=#{department},organization_version=organization_version+1 WHERE id=#{id} AND organization_version=#{version}") int membership(@Param("id")Long id,@Param("department")Long department,@Param("version")Long version);
 @Insert("INSERT INTO organization_change_request(request_type,status,requested_by,request_key,reason,department_name,target_user_id,source_department_id,target_department_id,proposed_manager_id,expected_current_department_id,expected_manager_id,expected_department_version,expected_user_version,task_id) VALUES(#{requestType},0,#{requestedBy},#{requestKey},#{reason},#{departmentName},#{targetUserId},#{sourceDepartmentId},#{targetDepartmentId},#{proposedManagerId},#{expectedCurrentDepartmentId},#{expectedManagerId},#{expectedDepartmentVersion},#{expectedUserVersion},#{taskId})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insertRequest(OrganizationChangeRequest r);
 @Select("SELECT * FROM organization_change_request WHERE id=#{id}") OrganizationChangeRequest request(Long id);
 @Select("SELECT * FROM organization_change_request WHERE requested_by=#{actor} AND request_key=#{key}") OrganizationChangeRequest byKey(@Param("actor")Long actor,@Param("key")String key);
 @Select("<script>SELECT * FROM organization_change_request <where><if test='actor!=null'>requested_by=#{actor}</if></where> ORDER BY id DESC LIMIT #{offset},#{size}</script>") List<OrganizationChangeRequest> requests(@Param("actor")Long actor,@Param("offset")int offset,@Param("size")int size);
 @Update("UPDATE organization_change_request SET status=#{status},processed_by=#{actor},decision_reason=#{reason},processed_time=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status=0")
 int resolve(@Param("id")Long id,@Param("status")int status,@Param("actor")Long actor,@Param("reason")String reason);
 @Insert("INSERT INTO organization_change_history(request_id,action_type,target_user_id,old_department_id,new_department_id,old_manager_id,new_manager_id,operator_user_id,approver_user_id,reason,bypass_approval) VALUES(#{requestId},#{actionType},#{targetUserId},#{oldDepartmentId},#{newDepartmentId},#{oldManagerId},#{newManagerId},#{operatorUserId},#{approverUserId},#{reason},#{bypassApproval})")
 @Options(useGeneratedKeys=true,keyProperty="id") int history(OrganizationChangeHistory h);
 @Select("SELECT * FROM organization_change_history ORDER BY id DESC LIMIT #{offset},#{size}") List<OrganizationChangeHistory> historyPage(@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT * FROM organization_change_history <where><if test='operator!=null'>operator_user_id=#{operator}</if><if test='action!=null'> AND action_type=#{action}</if><if test='from!=null'> AND effective_time&gt;=#{from}</if><if test='to!=null'> AND effective_time&lt;#{to}</if></where> ORDER BY effective_time DESC,id DESC LIMIT #{offset},#{size}</script>")List<OrganizationChangeHistory> historySearch(@Param("operator")Long operator,@Param("action")Integer action,@Param("from")java.time.LocalDateTime from,@Param("to")java.time.LocalDateTime to,@Param("offset")int offset,@Param("size")int size);
 @Select("<script>SELECT COUNT(*) FROM organization_change_history <where><if test='operator!=null'>operator_user_id=#{operator}</if><if test='action!=null'> AND action_type=#{action}</if><if test='from!=null'> AND effective_time&gt;=#{from}</if><if test='to!=null'> AND effective_time&lt;#{to}</if></where></script>")long historyCount(@Param("operator")Long operator,@Param("action")Integer action,@Param("from")java.time.LocalDateTime from,@Param("to")java.time.LocalDateTime to);
 @Select("SELECT DISTINCT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.role_code='HR_ADMIN' ORDER BY u.id") List<Long> hrs();
 @Select("SELECT d.* FROM todo d JOIN hotel_task t ON t.id=d.task_id WHERE d.user_id=#{id} AND d.is_active=1 AND d.status<>2 AND t.task_type=2 ORDER BY d.id DESC LIMIT 100") List<com.johnny.hotel.task.Todo> approvalTodos(Long id);
}
