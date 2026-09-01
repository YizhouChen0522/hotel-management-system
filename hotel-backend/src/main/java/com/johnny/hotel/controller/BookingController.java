package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.CreateBookingRequest;
import com.johnny.hotel.dto.UpdateBookingRequest;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.vo.BookingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<BookingVO> createBooking(@Valid @RequestBody CreateBookingRequest request,
                                           Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.createBooking(request, currentUserId));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<List<BookingVO>> getMyBookings(Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.getMyBookings(currentUserId));
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<BookingVO> cancelBooking(@PathVariable Long bookingId,
                                           Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.cancelBooking(bookingId, currentUserId));
    }
    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<BookingVO> getMyBookingById(@PathVariable Long bookingId,
                                              Authentication authentication) {

        Long currentUserId = (Long) authentication.getDetails();

        return Result.success(
                bookingService.getMyBookingById(
                        bookingId,
                        currentUserId
                )
        );
    }
    @PutMapping("/{bookingId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<BookingVO> updateBooking(@PathVariable Long bookingId,
                                           @Valid @RequestBody UpdateBookingRequest request,
                                           Authentication authentication) {

        Long currentUserId = (Long) authentication.getDetails();

        return Result.success(
                bookingService.updateBooking(
                        bookingId,
                        request,
                        currentUserId
                )
        );
    }
}
