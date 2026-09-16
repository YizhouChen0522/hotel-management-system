package com.johnny.hotel.organization;
import com.johnny.hotel.common.Result;import com.johnny.hotel.task.*;import java.util.List;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequiredArgsConstructor @RequestMapping("/api/organization")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','HR_ADMIN','SUPER_ADMIN')")
public class OrganizationController {
 private final OrganizationService service;
 @GetMapping("/departments") public Result<List<Department>> departments(@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return Result.success(service.departments(page,size));}
 @GetMapping("/departments/{id}") public Result<Department> department(@PathVariable Long id){return Result.success(service.departmentDetails(id));}
 @GetMapping("/requests") public Result<List<OrganizationChangeRequest>> requests(@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return Result.success(service.requests(page,size));}
 @PostMapping("/requests") public Result<OrganizationChangeRequest> create(@Valid @RequestBody OrganizationRequests.Change r){return Result.success(service.create(r));}
 @GetMapping("/requests/{id}") public Result<OrganizationChangeRequest> get(@PathVariable Long id){return Result.success(service.get(id));}
 @GetMapping("/requests/{id}/task") public Result<TaskView> task(@PathVariable Long id){return Result.success(service.task(id));}
 @GetMapping("/todos/my") @PreAuthorize("hasRole('HR_ADMIN')") public Result<List<Todo>> todos(){return Result.success(service.myTodos());}
 @PostMapping("/requests/{id}/claim") @PreAuthorize("hasRole('HR_ADMIN')") public Result<OrganizationChangeRequest> claim(@PathVariable Long id){return Result.success(service.claim(id));}
 @PostMapping("/requests/{id}/approve") @PreAuthorize("hasRole('HR_ADMIN')") public Result<OrganizationChangeRequest> approve(@PathVariable Long id){return Result.success(service.approve(id));}
 @PostMapping("/requests/{id}/reject") @PreAuthorize("hasRole('HR_ADMIN')") public Result<OrganizationChangeRequest> reject(@PathVariable Long id,@Valid @RequestBody OrganizationRequests.Decision r){return Result.success(service.reject(id,r.getReason()));}
 @PostMapping("/requests/{id}/cancel") public Result<OrganizationChangeRequest> cancel(@PathVariable Long id,@Valid @RequestBody OrganizationRequests.Decision r){return Result.success(service.cancel(id,r.getReason()));}
 @GetMapping("/history") public Result<com.johnny.hotel.pagination.PageResult<OrganizationChangeHistory>> history(@RequestParam(required=false)Long operatorId,@RequestParam(required=false)Integer actionType,@RequestParam(required=false)java.time.LocalDateTime from,@RequestParam(required=false)java.time.LocalDateTime to,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize){return Result.success(service.history(operatorId,actionType,from,to,page,pageSize));}
 @PostMapping("/direct") @PreAuthorize("hasRole('SUPER_ADMIN')") public Result<OrganizationChangeHistory> direct(@Valid @RequestBody OrganizationRequests.Change r){return Result.success(service.direct(r));}
}
