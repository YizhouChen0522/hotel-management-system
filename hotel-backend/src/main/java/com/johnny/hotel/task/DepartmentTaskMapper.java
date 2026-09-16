package com.johnny.hotel.task;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DepartmentTaskMapper {
 @Update("UPDATE hotel_task SET department_id=#{department},department_branch=#{branch},department_root=#{root} WHERE id=#{id}")
 int scope(@Param("id")Long id,@Param("department")Long department,@Param("branch")int branch,@Param("root")int root);
 @Update("UPDATE hotel_task SET routing_source=#{source} WHERE id=#{id}") int source(@Param("id")Long id,@Param("source")Integer source);
 @Select("SELECT * FROM hotel_task WHERE department_id=#{department} AND department_branch=1 AND status IN (0,1,4) ORDER BY parent_task_id,id") List<HotelTask> openBranches(Long department);
 @Select("SELECT EXISTS(SELECT 1 FROM hotel_task WHERE department_id=#{department} AND department_branch=1 AND status IN (0,1,4))") boolean hasOpenBranches(Long department);
 @Select("SELECT u.id FROM sys_user u WHERE u.department_id=#{department} AND u.status=1 AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code IN ('STAFF','MANAGER','OWNER','SUPER_ADMIN')) AND NOT EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code IN ('CUSTOMER','HR_ADMIN','FINANCE')) ORDER BY u.id") List<Long> members(Long department);
}
