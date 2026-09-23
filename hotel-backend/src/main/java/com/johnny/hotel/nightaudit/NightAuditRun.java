package com.johnny.hotel.nightaudit;
import lombok.*;import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NightAuditRun {private Long id;private LocalDate businessDate;private String requestKey;private String status;private String lastStep;private Long startedBy;private LocalDateTime startedAt;private LocalDateTime completedAt;private Integer attemptCount;private String lastError;@JsonIgnore private String processingToken;@JsonIgnore private LocalDateTime processingUntil;private LocalDateTime updateTime;}
