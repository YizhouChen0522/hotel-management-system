package com.johnny.hotel.service;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.dto.CreateBookingRequest;
import com.johnny.hotel.vo.BookingVO;

import java.util.List;

public interface BookingService {
    BookingVO createBooking(CreateBookingRequest request, Long currentUserId);

    List<BookingVO> getMyBookings(Long currentUserId);

    List<BookingVO> getBookingsPage(Integer page, Integer size);

    List<BookingVO> getPendingBookingsPage(Integer page, Integer size);

    BookingVO approveBooking(Long bookingId, ApproveBookingRequest request);

    BookingVO checkIn(Long bookingId);

    BookingVO checkOut(Long bookingId);

    BookingVO cancelBooking(Long bookingId, Long currentUserId);
}
