package com.johnny.hotel.stay;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.ChangeRoomDuringStayRequest;
import com.johnny.hotel.pagination.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/stays")
@PreAuthorize("hasAnyRole('CUSTOMER','STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
public class StayController {
    private final StayQueryService queries;
    private final StayApplicationService application;
    private final StayAccess access;
    private final StayGuestService guests;
    @GetMapping public Result<PageResult<Stay>> list(@RequestParam(required=false)Long bookingId,@RequestParam(required=false)Integer status,
                                                    @RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize) {
        return Result.success(queries.page(bookingId,status,page,pageSize));
    }
    @GetMapping("/{id}") public Result<Stay> get(@PathVariable Long id){return Result.success(queries.get(id));}
    @GetMapping("/{id}/guests") public Result<java.util.List<StayGuestService.Entry>> guests(@PathVariable Long id){return Result.success(guests.list(id));}
    @PostMapping("/{id}/guests") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<StayGuestService.Entry> addGuest(@PathVariable Long id,@Valid @RequestBody com.johnny.hotel.guest.GuestRequests.Add request){return Result.success(guests.addAccompanying(id,request));}
    @GetMapping("/{id}/assignments") public Result<java.util.List<com.johnny.hotel.entity.StayRoomAssignment>> assignments(@PathVariable Long id){return Result.success(queries.assignments(id));}
    @PostMapping("/{id}/check-out") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<Stay> checkout(@PathVariable Long id){return Result.success(application.checkOut(id,access.actor()));}
    @PostMapping("/{id}/room-change") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<Stay> roomChange(@PathVariable Long id,@Valid @RequestBody ChangeRoomDuringStayRequest request){return Result.success(application.changeRoomDuringStay(id,request,access.actor()));}
}
