package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RoomRateRequest;
import com.johnny.hotel.service.RoomRateService;
import com.johnny.hotel.vo.RoomRateVO;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/room-types/{typeId}/rates")
public class AdminRoomRateController {
    private final RoomRateService service;
    @GetMapping @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<List<RoomRateVO>> range(@PathVariable Long typeId,@RequestParam LocalDate start,@RequestParam LocalDate end){return Result.success(service.range(typeId,start,end));}
    @PutMapping("/{date}") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')")
    public Result<RoomRateVO> save(@PathVariable Long typeId,@PathVariable LocalDate date,@Valid @RequestBody RoomRateRequest request){return Result.success(service.save(typeId,date,request));}
    @DeleteMapping("/{date}") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')")
    public Result<Void> remove(@PathVariable Long typeId,@PathVariable LocalDate date){service.remove(typeId,date);return Result.success();}
}
