package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.vo.BookingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsPage(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsPage(page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getPendingBookingsPage(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getPendingBookingsPage(page, size));
    }

    @PostMapping("/{bookingId}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> approveBooking(@PathVariable Long bookingId,
                                            @Valid @RequestBody ApproveBookingRequest request,
                                            Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.approveBooking(bookingId, request, currentUserId));
    }

    @PostMapping("/{bookingId}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> rejectBooking(@PathVariable Long bookingId,
                                           Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.rejectBooking(bookingId, currentUserId));
    }

    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkIn(@PathVariable Long bookingId,
                                     Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.checkIn(bookingId, currentUserId));
    }

    @PostMapping("/{bookingId}/check-out")
    @PreAuthorize("hasAnyRole('STAFF', 'HR_ADMIN', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkOut(@PathVariable Long bookingId,
                                      Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.checkOut(bookingId, currentUserId));
    }
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsByStatus(@RequestParam Integer status,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsByStatus(status, page, size));
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsByUserId(@RequestParam Long userId,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsByUserId(userId, page, size));
    }

    @GetMapping("/check-in-range")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsByCheckInDateRange(@RequestParam LocalDate startDate,
                                                                 @RequestParam LocalDate endDate,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsByCheckInDateRange(startDate, endDate, page, size));
    }

    @GetMapping("/check-out-range")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<List<BookingVO>> getBookingsByCheckOutDateRange(@RequestParam LocalDate startDate,
                                                                  @RequestParam LocalDate endDate,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(bookingService.getBookingsByCheckOutDateRange(startDate, endDate, page, size));
    }
}
