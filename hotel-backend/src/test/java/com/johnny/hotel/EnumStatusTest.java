package com.johnny.hotel;

import com.johnny.hotel.enums.*;
import com.johnny.hotel.wallet.TopUpStatus;
import com.johnny.hotel.wallet.WalletStatus;
import com.johnny.hotel.wallet.WalletTransactionType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies status enums against the V1-V11 schema definitions:
 * round-trip fromCode(getCode()), unknown-code rejection and exact code values.
 */
class EnumStatusTest {

    @Test void roundTripAllEnums() {
        for (BookingStatus s : BookingStatus.values()) assertEquals(s, BookingStatus.fromCode(s.getCode()));
        for (RoomStatus s : RoomStatus.values()) assertEquals(s, RoomStatus.fromCode(s.getCode()));
        for (RoomTypeStatus s : RoomTypeStatus.values()) assertEquals(s, RoomTypeStatus.fromCode(s.getCode()));
        for (UserStatus s : UserStatus.values()) assertEquals(s, UserStatus.fromCode(s.getCode()));
        for (RefundStatus s : RefundStatus.values()) assertEquals(s, RefundStatus.fromCode(s.getCode()));
        for (WalletStatus s : WalletStatus.values()) assertEquals(s, WalletStatus.fromCode(s.getCode()));
        for (TopUpStatus s : TopUpStatus.values()) assertEquals(s, TopUpStatus.fromCode(s.getCode()));
        for (WalletTransactionType s : WalletTransactionType.values()) assertEquals(s, WalletTransactionType.fromCode(s.getCode()));
    }

    @Test void unknownCodesRejected() {
        assertThrows(IllegalArgumentException.class, () -> BookingStatus.fromCode(6));
        assertThrows(IllegalArgumentException.class, () -> BookingStatus.fromCode(-1));
        assertThrows(IllegalArgumentException.class, () -> RoomStatus.fromCode(5));
        assertThrows(IllegalArgumentException.class, () -> RoomTypeStatus.fromCode(2));
        assertThrows(IllegalArgumentException.class, () -> UserStatus.fromCode(4));
        assertThrows(IllegalArgumentException.class, () -> RefundStatus.fromCode(3));
        assertThrows(IllegalArgumentException.class, () -> WalletStatus.fromCode(2));
        assertThrows(IllegalArgumentException.class, () -> TopUpStatus.fromCode(3));
        assertThrows(IllegalArgumentException.class, () -> WalletTransactionType.fromCode(0));
    }

    @Test void codesMatchSchemaDefinitions() {
        // V1 booking: 0 Pending, 1 Approved, 2 Checked In, 3 Checked Out, 4 Cancelled, 5 Rejected
        assertEquals(0, BookingStatus.PENDING.getCode());
        assertEquals(1, BookingStatus.APPROVED.getCode());
        assertEquals(2, BookingStatus.CHECKED_IN.getCode());
        assertEquals(3, BookingStatus.CHECKED_OUT.getCode());
        assertEquals(4, BookingStatus.CANCELLED_BY_USER.getCode());
        assertEquals(5, BookingStatus.REJECTED_BY_STAFF.getCode());
        // V1 room: 0 Disabled, 1 Available, 2 Booked, 3 Maintenance, 4 Occupied
        assertEquals(0, RoomStatus.DISABLED.getCode());
        assertEquals(1, RoomStatus.AVAILABLE.getCode());
        assertEquals(2, RoomStatus.BOOKED.getCode());
        assertEquals(3, RoomStatus.MAINTENANCE.getCode());
        assertEquals(4, RoomStatus.OCCUPIED.getCode());
        // V1 room_type: 0 Disabled, 1 Enabled
        assertEquals(0, RoomTypeStatus.DISABLED.getCode());
        assertEquals(1, RoomTypeStatus.ENABLED.getCode());
        // V1 sys_user: 0 Disabled, 1 Active, 2 Pending, 3 Rejected
        assertEquals(0, UserStatus.INACTIVE.getCode());
        assertEquals(1, UserStatus.ACTIVE.getCode());
        assertEquals(2, UserStatus.PENDING.getCode());
        assertEquals(3, UserStatus.REJECTED.getCode());
        // V11 refund: 0 PENDING, 1 SUCCESS, 2 FAILED
        assertEquals(0, RefundStatus.PENDING.getCode());
        assertEquals(1, RefundStatus.SUCCESS.getCode());
        assertEquals(2, RefundStatus.FAILED.getCode());
        // V10 wallet: 0 BLOCKED, 1 ACTIVE
        assertEquals(0, WalletStatus.BLOCKED.getCode());
        assertEquals(1, WalletStatus.ACTIVE.getCode());
        // V10 top-up: 0 PENDING, 1 SUCCESS, 2 REJECTED
        assertEquals(0, TopUpStatus.PENDING.getCode());
        assertEquals(1, TopUpStatus.SUCCESS.getCode());
        assertEquals(2, TopUpStatus.REJECTED.getCode());
        // V10 wallet_transaction types: 1 TOP_UP, 2 REFUND_CREDIT, 3 FOLIO_PAYMENT, 4 EMPLOYEE_BENEFIT
        assertEquals(1, WalletTransactionType.TOP_UP.getCode());
        assertEquals(2, WalletTransactionType.REFUND_CREDIT.getCode());
        assertEquals(3, WalletTransactionType.FOLIO_PAYMENT.getCode());
        assertEquals(4, WalletTransactionType.EMPLOYEE_BENEFIT.getCode());
    }

    @Test void noDuplicateCodesWithinEnum() {
        assertDistinct(BookingStatus.values(), BookingStatus::getCode);
        assertDistinct(RoomStatus.values(), RoomStatus::getCode);
        assertDistinct(RoomTypeStatus.values(), RoomTypeStatus::getCode);
        assertDistinct(UserStatus.values(), UserStatus::getCode);
        assertDistinct(RefundStatus.values(), RefundStatus::getCode);
        assertDistinct(WalletStatus.values(), WalletStatus::getCode);
        assertDistinct(TopUpStatus.values(), TopUpStatus::getCode);
        assertDistinct(WalletTransactionType.values(), WalletTransactionType::getCode);
    }

    private static <E extends Enum<E>> void assertDistinct(E[] values, java.util.function.ToIntFunction<E> code) {
        Map<Integer, String> seen = new HashMap<>();
        for (E v : values) {
            String prev = seen.put(code.applyAsInt(v), v.name());
            assertNull(prev, "duplicate code in " + v.getDeclaringClass().getSimpleName() + ": " + code.applyAsInt(v));
        }
    }
}
