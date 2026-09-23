package com.johnny.hotel.nightaudit;
import lombok.*;
import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NightAuditBlocker {private Long id;private Long runId;private String blockerType;private String objectType;private Long objectId;private String reason;private Integer active;private LocalDateTime detectedAt;private LocalDateTime resolvedAt;}
