package com.johnny.hotel.guest;
import lombok.*;import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class GuestProfile {private Long id;private Long linkedUserId;private String firstName;private String lastName;private String phone;private String email;private String gender;private LocalDate dateOfBirth;private String nationality;private String documentType;private String documentNumber;private String issuingCountry;private LocalDate documentExpiryDate;private String notes;private Integer status;private LocalDateTime createTime;private LocalDateTime updateTime;}
