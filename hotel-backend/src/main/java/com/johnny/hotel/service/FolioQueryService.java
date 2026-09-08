package com.johnny.hotel.service;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.vo.*;
import com.johnny.hotel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service
@RequiredArgsConstructor
public class FolioQueryService {
    private final FolioMapper folios;
    private final BookingMapper bookings;
    private final FolioItemMapper items;
    private final PaymentMapper payments;

    @Transactional
    public FolioVO byBooking(Long bookingId, Long customerId) {
        return view(folios.selectByBookingIdForUpdate(bookingId), customerId);
    }
    @Transactional
    public FolioVO byFolio(Long folioId, Long customerId) {
        return view(folios.selectByIdForUpdate(folioId), customerId);
    }
    private FolioVO view(Folio f, Long customerId) {
        require(f != null, "Folio does not exist");
        Booking b = bookings.selectById(f.getBookingId()); // Immutable owner, deliberately no Booking lock after Folio.
        require(b != null, "Booking does not exist");
        if (customerId != null && !customerId.equals(b.getUserId())) throw new BusinessException(403, "Forbidden");
        return FolioVO.builder().id(f.getId()).bookingId(f.getBookingId()).currency(f.getCurrency()).status(f.getStatus())
                .totalAmount(f.getTotalAmount()).paidAmount(f.getPaidAmount()).balanceAmount(f.getBalanceAmount()).closedTime(f.getClosedTime())
                .items(items.selectByFolioIdForUpdate(f.getId()).stream().map(FolioVO.Item::from).toList())
                .payments(payments.selectByFolioIdForUpdate(f.getId()).stream().map(PaymentVO::from).toList()).build();
    }
}
