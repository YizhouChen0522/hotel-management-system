package com.johnny.hotel.walkin;

import com.johnny.hotel.guest.GuestRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

public final class WalkInRequests {
    private WalkInRequests() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Create {
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") private String requestKey;
        private Long bookerGuestId;
        @Valid private GuestRequests.Profile bookerProfile;
        private Long primaryGuestId;
        @Valid private GuestRequests.Profile primaryProfile;
        @Valid private List<GuestRequests.Add> accompanyingGuests;
        @NotNull private Long roomTypeId;
        @NotNull private Long reservedRoomId;
        @NotNull @Min(1) private Integer guestCount;
        @NotNull private LocalDate checkInDate;
        @NotNull private LocalDate checkOutDate;
    }
}
