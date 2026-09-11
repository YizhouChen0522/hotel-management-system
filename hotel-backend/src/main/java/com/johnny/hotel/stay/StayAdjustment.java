package com.johnny.hotel.stay;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StayAdjustment {
    private Long id,bookingId,folioId,assignmentId,operatorId,previousLateId,conflictBookingId,basisItemId;
    private Integer adjustmentType;
    private LocalDate oldEnd,newEnd;
    private LocalDateTime effectiveTime,createTime;
    private String requestKey,reason,rateSource;
    private BigDecimal lockedRate;
    public String getOperationalNotice(){return conflictBookingId==null?null:"Conflicting booking "+conflictBookingId+" requires manual relocation / Room Change; no room was changed automatically";}
}
