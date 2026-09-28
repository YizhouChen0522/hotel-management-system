package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.dto.ChangeBookingRoomTypeRequest;
import com.johnny.hotel.dto.ChangeRoomDuringStayRequest;
import com.johnny.hotel.dto.ReassignRoomRequest;
import com.johnny.hotel.service.BookingService;
import com.johnny.hotel.vo.BookingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.johnny.hotel.pagination.PageResult;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getBookingsPage(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize,
                                                   @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pageBookings(page, pageSize!=null?pageSize:size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getPendingBookingsPage(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize,
                                                          @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pagePendingBookings(page, pageSize!=null?pageSize:size));
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
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkIn(@PathVariable Long bookingId,
                                     Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.checkIn(bookingId, currentUserId));
    }

    @PostMapping("/{bookingId}/check-out")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> checkOut(@PathVariable Long bookingId,
                                      Authentication authentication) {
        Long currentUserId = (Long) authentication.getDetails();
        return Result.success(bookingService.checkOut(bookingId, currentUserId));
    }
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getBookingsByStatus(@RequestParam Integer status,
                                                       @RequestParam(required=false) Integer page,@RequestParam(required=false)Integer pageSize,
                                                       @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pageBookingsByStatus(status, page, pageSize!=null?pageSize:size));
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getBookingsByUserId(@RequestParam Long userId,
                                                       @RequestParam(required=false) Integer page,@RequestParam(required=false)Integer pageSize,
                                                       @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pageBookingsByUserId(userId, page, pageSize!=null?pageSize:size));
    }

    @GetMapping("/check-in-range")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getBookingsByCheckInDateRange(@RequestParam LocalDate startDate,
                                                                 @RequestParam LocalDate endDate,
                                                                 @RequestParam(required=false) Integer page,@RequestParam(required=false)Integer pageSize,
                                                                 @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pageBookingsByCheckInDateRange(startDate, endDate, page, pageSize!=null?pageSize:size));
    }

    @GetMapping("/check-out-range")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<PageResult<BookingVO>> getBookingsByCheckOutDateRange(@RequestParam LocalDate startDate,
                                                                  @RequestParam LocalDate endDate,
                                                                  @RequestParam(required=false) Integer page,@RequestParam(required=false)Integer pageSize,
                                                                  @RequestParam(required=false) Integer size) {
        return Result.success(bookingService.pageBookingsByCheckOutDateRange(startDate, endDate, page, pageSize!=null?pageSize:size));
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> getBookingById(@PathVariable Long bookingId) {

        return Result.success(
                bookingService.getBookingByIdForAdmin(bookingId)
        );
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> cancelBookingByAdmin(@PathVariable Long bookingId,
                                                  Authentication authentication) {

        Long currentUserId = (Long) authentication.getDetails();

        return Result.success(
                bookingService.cancelBookingByAdmin(
                        bookingId,
                        currentUserId
                )
        );
    }

    @PutMapping("/{bookingId}/assigned-room")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> reassignRoom(@PathVariable Long bookingId,
                                          @Valid @RequestBody ReassignRoomRequest request,
                                          Authentication authentication) {

        Long currentUserId = (Long) authentication.getDetails();

        return Result.success(
                bookingService.reassignRoom(
                        bookingId,
                        request,
                        currentUserId
                )
        );
    }

    @PutMapping("/{bookingId}/room-type")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'OWNER', 'SUPER_ADMIN')")
    public Result<BookingVO> changeRoomType(
            @PathVariable Long bookingId,
            @Valid @RequestBody ChangeBookingRoomTypeRequest request,
            Authentication authentication) {

        Long currentUserId = (Long) authentication.getDetails();

        return Result.success(
                bookingService.changeRoomType(
                        bookingId,
                        request,
                        currentUserId
                )
        );
    }

    @PutMapping("/{bookingId}/room-during-stay")
    @PreAuthorize(
            "hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')"
    )
    public Result<BookingVO> changeRoomDuringStay(
            @PathVariable Long bookingId,
            @Valid @RequestBody
            ChangeRoomDuringStayRequest request,
            Authentication authentication) {

        Long currentUserId =
                (Long) authentication.getDetails();

        return Result.success(
                bookingService.changeRoomDuringStay(
                        bookingId,
                        request,
                        currentUserId
                )
        );
    }




}
