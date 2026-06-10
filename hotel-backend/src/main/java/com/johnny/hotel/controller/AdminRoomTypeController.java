package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RoomTypeRequest;
import com.johnny.hotel.service.RoomTypeService;
import com.johnny.hotel.vo.RoomTypeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/room-types")
@RequiredArgsConstructor
public class AdminRoomTypeController {
    private final RoomTypeService roomTypeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomTypeVO> createRoomType(@Valid @RequestBody RoomTypeRequest request) {
        // Implementation for creating a new room type
        RoomTypeVO createdRoomType = roomTypeService.createRoomType(request);
        return Result.success(createdRoomType);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<RoomTypeVO>> getRoomTypes() {
        // Implementation for retrieving all room types
        List<RoomTypeVO> roomTypes = roomTypeService.getRoomTypes();
        return Result.success(roomTypes);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomTypeVO> getRoomTypeById(@PathVariable Long id) {
        return Result.success(roomTypeService.getRoomTypeById(id));
    }
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomTypeVO> getRoomTypeByName(@RequestParam String typeName) {
        return Result.success(roomTypeService.getRoomTypeByName(typeName));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomTypeVO> updateRoomType(@PathVariable Long id,
                                             @Valid @RequestBody RoomTypeRequest request) {
        return Result.success(roomTypeService.updateRoomType(id, request));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> disableRoomType(@PathVariable Long id) {
        roomTypeService.disableRoomType(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> enableRoomType(@PathVariable Long id) {
        roomTypeService.enableRoomType(id);
        return Result.success();
    }


}
