package com.johnny.hotel.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingVO {
    private Long id;

    private Long userId;

    private Long roomTypeId;

    private String roomTypeName;

    private Long assignedRoomId;

    private String assignedRoomNumber;

    private Integer guestCount;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    private Integer status;

    private BigDecimal totalPrice;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
