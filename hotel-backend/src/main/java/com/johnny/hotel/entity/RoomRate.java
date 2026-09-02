package com.johnny.hotel.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RoomRate {

    private Long id;

    private Long roomTypeId;

    private LocalDate rateDate;

    private BigDecimal price;

    private String rateSource;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
