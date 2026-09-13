package com.johnny.hotel.task;
import com.johnny.hotel.common.Result; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/todos")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class TodoController {
 private final TodoService service;
 @GetMapping("/my") public Result<List<Todo>> mine(@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return Result.success(service.mine(page,size));}
 @GetMapping("/my/{id}") public Result<Todo> get(@PathVariable Long id){return Result.success(service.get(id));}
 @PostMapping("/{id}/acknowledge") public Result<Todo> acknowledge(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.acknowledge(id,r==null?null:r.getNote()));}
 @PostMapping("/{id}/block") public Result<Todo> block(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.block(id,r==null?null:r.getNote()));}
 @PostMapping("/{id}/resume") public Result<Todo> resume(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.resume(id,r==null?null:r.getNote()));}
 @PostMapping("/{id}/complete") public Result<Todo> complete(@PathVariable Long id,@Valid @RequestBody(required=false)TaskRequests.Note r){return Result.success(service.complete(id,r==null?null:r.getNote()));}
}
