package com.johnny.hotel.pricing; import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class BookingWindowBand{private Long id,policyId;private String label;private Integer minDays,maxDays,sortOrder;}
