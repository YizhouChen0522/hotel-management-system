package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RoomRequest;
import com.johnny.hotel.service.RoomService;
import com.johnny.hotel.vo.RoomTypeVO;
import com.johnny.hotel.vo.RoomVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class AdminRoomController {

    private final RoomService roomService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomVO> createRoom(@Valid @RequestBody RoomRequest request) {
        return Result.success(roomService.createRoom(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<RoomVO>> getRooms() {
        return Result.success(roomService.getRooms());
    }
    @GetMapping("/floor")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<RoomVO>> getRoomsByFloor(@RequestParam Integer floor) {
        return Result.success(roomService.getRoomsByFloor(floor));
    }
    @GetMapping("/number")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomVO> getRoomByNumber(@RequestParam String roomNumber ) {
        return Result.success(roomService.getRoomByNumber(roomNumber));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomVO> getRoomById(@PathVariable Long id) {
        return Result.success(roomService.getRoomById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<RoomVO> updateRoom(@PathVariable Long id,
                                     @Valid @RequestBody RoomRequest request) {
        return Result.success(roomService.updateRoom(id, request));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> disableRoom(@PathVariable Long id) {
        roomService.disableRoom(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<Void> enableRoom(@PathVariable Long id) {
        roomService.enableRoom(id);
        return Result.success();
    }
}
