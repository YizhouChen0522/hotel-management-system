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
    private Long roomTypeId;
    private Long assignedRoomId;
    private Integer guestCount;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer status;
    private BigDecimal totalPrice;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
