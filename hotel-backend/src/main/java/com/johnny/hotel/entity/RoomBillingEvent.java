package com.johnny.hotel.entity;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoomBillingEvent {
    private Long id;
    private Long bookingId;
    private Long oldAssignmentId;
    private Long newAssignmentId;
    private LocalDate changeDate;
    private BigDecimal newChargesTotal;
}
