package com.johnny.hotel.service;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.dto.CreateBookingRequest;
import com.johnny.hotel.vo.BookingVO;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    BookingVO createBooking(CreateBookingRequest request, Long currentUserId);

    List<BookingVO> getMyBookings(Long currentUserId);

    List<BookingVO> getBookingsPage(Integer page, Integer size);

    List<BookingVO> getPendingBookingsPage(Integer page, Integer size);

    BookingVO approveBooking(Long bookingId, ApproveBookingRequest request, Long currentUserId);

    BookingVO rejectBooking(Long bookingId, Long currentUserId);

    BookingVO checkIn(Long bookingId, Long currentUserId);

    BookingVO checkOut(Long bookingId, Long currentUserId);

    BookingVO cancelBooking(Long bookingId, Long currentUserId);

    List<BookingVO> getBookingsByStatus(Integer status, Integer page, Integer size);

    List<BookingVO> getBookingsByUserId(Long userId, Integer page, Integer size);

    List<BookingVO> getBookingsByCheckInDateRange(LocalDate startDate, LocalDate endDate, Integer page, Integer size);

    List<BookingVO> getBookingsByCheckOutDateRange(LocalDate startDate, LocalDate endDate, Integer page, Integer size);
}
