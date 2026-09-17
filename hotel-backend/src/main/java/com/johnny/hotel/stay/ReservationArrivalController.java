package com.johnny.hotel.stay;
import com.johnny.hotel.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class ReservationArrivalController {
    private final StayApplicationService application;
    private final StayAccess access;
    @PostMapping("/api/bookings/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<Stay> checkIn(@PathVariable Long bookingId){return Result.success(application.checkIn(bookingId,access.actor()));}
}
