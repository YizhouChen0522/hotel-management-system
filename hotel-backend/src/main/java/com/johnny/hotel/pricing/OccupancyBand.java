package com.johnny.hotel.pricing; import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class OccupancyBand{private Long id,policyId;private String label;private Integer lowerInclusive,upperExclusive,sortOrder;}
