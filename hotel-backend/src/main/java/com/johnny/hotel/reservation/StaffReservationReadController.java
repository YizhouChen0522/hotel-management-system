package com.johnny.hotel.reservation;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.vo.RoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bookings/{bookingId}")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class StaffReservationReadController {
    private final StaffReservationReadService service;

    @GetMapping("/eligible-rooms")
    public Result<List<RoomVO>> eligibleRooms(@PathVariable Long bookingId) {
        return Result.success(service.eligibleRooms(bookingId));
    }

    @GetMapping("/price-snapshot")
    public Result<StaffReservationReadService.PriceSnapshot> priceSnapshot(@PathVariable Long bookingId) {
        return Result.success(service.priceSnapshot(bookingId));
    }
}
