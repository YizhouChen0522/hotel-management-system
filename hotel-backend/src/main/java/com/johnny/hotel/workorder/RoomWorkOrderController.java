package com.johnny.hotel.workorder;
import com.johnny.hotel.common.Result;import com.johnny.hotel.task.TaskView;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/work-orders")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class RoomWorkOrderController {
 private final RoomWorkOrderService service;
 @PostMapping public Result<RoomWorkOrder> report(@Valid @RequestBody WorkOrderRequests.Report r){return Result.success(service.report(r));}
 @GetMapping public Result<List<RoomWorkOrder>> list(@RequestParam(required=false)Integer status,@RequestParam(required=false)Long roomId,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){return Result.success(service.list(status,roomId,page,size));}
 @GetMapping("/{id}") public Result<RoomWorkOrder> get(@PathVariable Long id){return Result.success(service.get(id));}
 @GetMapping("/{id}/task") public Result<TaskView> task(@PathVariable Long id){return Result.success(service.task(id));}
 @PostMapping("/{id}/complete") public Result<RoomWorkOrder> complete(@PathVariable Long id,@Valid @RequestBody(required=false)WorkOrderRequests.Complete r){return Result.success(service.complete(id,r,false));}
 @PostMapping("/{id}/force-complete") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')") public Result<RoomWorkOrder> force(@PathVariable Long id,@Valid @RequestBody(required=false)WorkOrderRequests.Complete r){return Result.success(service.complete(id,r,true));}
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')") public Result<RoomWorkOrder> cancel(@PathVariable Long id,@Valid @RequestBody(required=false)WorkOrderRequests.Note r){return Result.success(service.cancel(id,r==null?null:r.getNote()));}
}
