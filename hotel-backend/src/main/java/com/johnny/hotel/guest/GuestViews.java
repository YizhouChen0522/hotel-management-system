package com.johnny.hotel.guest;
import lombok.*;import java.time.*;import java.util.*;
public final class GuestViews {private GuestViews(){}
 @Builder public record Profile(Long id,Long linkedUserId,String firstName,String lastName,String phone,String email,String gender,LocalDate dateOfBirth,String nationality,String documentType,String documentNumber,String issuingCountry,LocalDate documentExpiryDate,String notes,Integer status){}
 @Builder public record BookingEntry(Long relationId,Long bookingId,GuestRole role,Profile guest){}
 @Builder public record Registration(Long id,Long bookingId,Long primaryGuestId,Long registeredBy,LocalDateTime registeredTime,boolean confirmed){}
 @Builder public record Stay(Long bookingId,Long assignmentId,String roomNumber,String roomTypeName,LocalDateTime actualCheckInTime,LocalDateTime actualCheckOutTime){}
}
