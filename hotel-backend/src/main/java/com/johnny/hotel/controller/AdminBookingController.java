package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.vo.BookingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsPage(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsPage(page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getPendingBookingsPage(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getPendingBookingsPage(page, size));
    }

    @PostMapping("/{bookingId}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> approveBooking(@PathVariable Long bookingId,
                                            @Valid @RequestBody ApproveBookingRequest request) {
        return Result.success(bookingService.approveBooking(bookingId, request));
    }

    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkIn(@PathVariable Long bookingId) {
        return Result.success(bookingService.checkIn(bookingId));
    }

    @PostMapping("/{bookingId}/check-out")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkOut(@PathVariable Long bookingId) {
        return Result.success(bookingService.checkOut(bookingId));
    }
}
