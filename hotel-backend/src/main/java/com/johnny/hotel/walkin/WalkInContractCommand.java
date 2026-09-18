package com.johnny.hotel.walkin;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record WalkInContractCommand(Long bookerGuestId, Long roomTypeId, Long reservedRoomId,
                                    Integer guestCount, LocalDate checkInDate, LocalDate checkOutDate,
                                    String requestKey) {}
