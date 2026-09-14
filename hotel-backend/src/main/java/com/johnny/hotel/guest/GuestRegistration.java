package com.johnny.hotel.guest;
import lombok.*;import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class GuestRegistration {private Long id;private Long bookingId;private Long primaryGuestId;private Long registeredBy;private LocalDateTime registeredTime;private Boolean registrationConfirmed;private LocalDateTime createTime;private LocalDateTime updateTime;}
