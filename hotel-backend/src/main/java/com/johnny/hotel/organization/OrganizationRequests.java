package com.johnny.hotel.organization;
import lombok.*;import jakarta.validation.constraints.*;
public final class OrganizationRequests {
 private OrganizationRequests(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Change {
  @NotNull private OrganizationRequestType type;
  @Size(max=100) private String requestKey;
  @Size(max=500) private String reason;
  @Size(max=120) private String departmentName;
  private Long departmentId,targetUserId,proposedManagerId;
 }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Decision { @Size(max=500) private String reason; }
}
