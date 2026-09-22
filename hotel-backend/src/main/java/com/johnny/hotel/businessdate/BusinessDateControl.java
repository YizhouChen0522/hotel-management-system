package com.johnny.hotel.businessdate;
import lombok.*;
import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessDateControl { private Integer id; private LocalDate businessDate; private String state; private LocalDateTime initializedAt; private Long version; }
