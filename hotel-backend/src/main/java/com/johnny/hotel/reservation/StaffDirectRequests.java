package com.johnny.hotel.reservation;

import com.johnny.hotel.guest.GuestRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

public final class StaffDirectRequests {
    private StaffDirectRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Create {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
        private Long bookerGuestId;
        @Valid private GuestRequests.Profile bookerProfile;
        private Long primaryGuestId;
        @Valid private GuestRequests.Profile primaryProfile;
        @NotNull private Long roomTypeId;
        @NotNull private Long reservedRoomId;
        @NotNull @Min(1) private Integer guestCount;
        @NotNull private LocalDate checkInDate;
        @NotNull private LocalDate checkOutDate;
        @NotBlank @Pattern(regexp="CASH|CARD|BANK_TRANSFER") private String paymentMethod;
        @NotBlank @Size(max=100) private String receiptReference;
    }
}
