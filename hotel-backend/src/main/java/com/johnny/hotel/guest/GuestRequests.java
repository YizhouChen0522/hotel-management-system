package com.johnny.hotel.guest;
import jakarta.validation.constraints.*;import lombok.*;import java.time.*;
public final class GuestRequests {private GuestRequests(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Profile { @NotBlank @Size(max=80) private String firstName; @NotBlank @Size(max=80) private String lastName; @Size(max=30) private String phone; @Email @Size(max=120) private String email; @Size(max=20) private String gender; private LocalDate dateOfBirth; @Size(max=80) private String nationality; @Size(max=40) private String documentType; @Size(max=100) private String documentNumber; @Size(max=80) private String issuingCountry; private LocalDate documentExpiryDate; @Size(max=500) private String notes; }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Add { private Long guestId; private GuestRole role; @jakarta.validation.Valid private Profile profile; }
}
