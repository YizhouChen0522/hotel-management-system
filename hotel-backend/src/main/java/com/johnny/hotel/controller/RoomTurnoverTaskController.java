package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.CompleteTurnoverTaskRequest;
import com.johnny.hotel.entity.RoomTurnoverTask;
import com.johnny.hotel.service.RoomTurnoverTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/turnover-tasks")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class RoomTurnoverTaskController {
    private final RoomTurnoverTaskService service;
    @GetMapping public Result<List<RoomTurnoverTask>> list(@RequestParam(required=false) Integer status,@RequestParam(required=false) Long roomId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size){return Result.success(service.list(status,roomId,page,size));}
    @GetMapping("/{id}") public Result<RoomTurnoverTask> get(@PathVariable Long id){return Result.success(service.get(id));}
    @PostMapping("/{id}/accept") public Result<RoomTurnoverTask> accept(@PathVariable Long id){return Result.success(service.accept(id));}
    @PostMapping("/{id}/complete") public Result<RoomTurnoverTask> complete(@PathVariable Long id,@Valid @RequestBody(required=false) CompleteTurnoverTaskRequest request){return Result.success(service.complete(id,request==null?null:request.getNote()));}
}
