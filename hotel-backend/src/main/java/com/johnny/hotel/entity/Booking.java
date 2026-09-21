package com.johnny.hotel.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    private Long id;
    private Long userId;
    private Long bookerGuestProfileId;
    private Long createdByUserId;
    private Long roomTypeId;
    private Long reservedRoomId;
    private String portalRequestKey;
    private String reservationSource;
    private String walkInRequestKey;
    private String staffDirectRequestKey;
    private Long reservationPolicyId;
    private Integer guestCount;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer status; // 0 pending, 1 approved, 4 cancelled, 5 rejected, 6 no-show
    private BigDecimal totalPrice;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
