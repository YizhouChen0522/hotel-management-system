package com.johnny.hotel.task;
import com.johnny.hotel.common.Result; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/tasks") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class HotelTaskController { private final HotelTaskService service;
 @GetMapping public Result<List<HotelTask>> list(@RequestParam(required=false)Integer status,@RequestParam(required=false)Integer type,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return Result.success(service.list(status,type,page,size));}
 @GetMapping("/{id}") public Result<TaskView> get(@PathVariable Long id){return Result.success(service.get(id));}
 @PostMapping("/general") public Result<HotelTask> create(@Valid @RequestBody TaskRequests.CreateGeneral r){return Result.success(service.createGeneral(r));}
 @PostMapping("/{id}/claim") public Result<TaskView> claim(@PathVariable Long id){return Result.success(service.claim(id));}
 @PostMapping("/{id}/accept") public Result<TaskView> accept(@PathVariable Long id){return Result.success(service.accept(id));}
 @PostMapping("/{id}/complete") public Result<TaskView> complete(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.complete(id,r==null?null:r.getNote()));}
 @GetMapping("/my-todo") public Result<List<TaskAssignment>> todo(){return Result.success(service.myTodo());}
}
