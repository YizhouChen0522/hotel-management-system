package com.johnny.hotel.task;
import com.johnny.hotel.common.Result; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/tasks") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')")
public class AdminHotelTaskController { private final HotelTaskService service;
 @PostMapping("/{id}/assign") public Result<TaskView> assign(@PathVariable Long id,@Valid @RequestBody TaskRequests.Assign r){return Result.success(service.assign(id,r));}
 @PostMapping("/{id}/reassign") public Result<TaskView> reassign(@PathVariable Long id,@Valid @RequestBody TaskRequests.Assign r){return Result.success(service.reassign(id,r));}
 @PostMapping("/{id}/cancel") public Result<TaskView> cancel(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.cancel(id,r==null?null:r.getNote()));}
 @PostMapping("/{id}/force-complete") public Result<TaskView> force(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.forceComplete(id,r==null?null:r.getNote()));}
}
