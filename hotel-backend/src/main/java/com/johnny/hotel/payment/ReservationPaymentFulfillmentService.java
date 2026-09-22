package com.johnny.hotel.payment;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.enums.BookingStatus;
import com.johnny.hotel.guest.*;
import com.johnny.hotel.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class ReservationPaymentFulfillmentService {
    private final PaymentCoreMapper db;
    private final BookingMapper bookings;
    private final BookingPriceVersionMapper prices;
    private final BookingNightlyRateMapper nights;
    private final DepositService depositService;
    private final DepositMapper deposits;
    private final GuestMapper guests;
    private final SysUserMapper users;
    private final SysAuditLogMapper audits;
    private final Clock clock;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long fulfill(Long attemptId){
        var attempt=db.lockAttempt(attemptId);require(attempt!=null&&"SUCCEEDED".equals(attempt.getStatus()),"Only successful payments can be fulfilled");
        if("COMPLETED".equals(attempt.getFulfillmentStatus()))return attempt.getBookingId();
        var checkout=db.lockCheckout(attempt.getCheckoutSessionId());require(checkout!=null,"Checkout session is missing");
        require(attempt.getAmount().compareTo(checkout.getQuotedTotal())==0&&attempt.getCurrency().equals(checkout.getCurrency()),"Frozen checkout amount mismatch");
        var existing=bookings.selectByPortalRequestKeyForUpdate(checkout.getRequestKey());
        if(existing!=null){require(existing.getUserId().equals(checkout.getCustomerUserId())&&existing.getTotalPrice().compareTo(checkout.getQuotedTotal())==0,"Checkout request key conflicts with another booking");one(db.fulfilled(attemptId,existing.getId()));return existing.getId();}
        var booker=guests.byUser(checkout.getCustomerUserId());
        if(booker==null){var user=users.selectById(checkout.getCustomerUserId());require(user!=null,"Customer account does not exist");String display=user.getRealName()==null||user.getRealName().isBlank()?user.getUsername():user.getRealName().trim();String[] names=display.split("\\s+",2);booker=GuestProfile.builder().linkedUserId(user.getId()).firstName(names[0]).lastName(names.length==2?names[1]:names[0]).phone(user.getPhone()).email(user.getEmail()).status(1).build();one(guests.insertProfile(booker));}
        var booking=Booking.builder().userId(checkout.getCustomerUserId()).bookerGuestProfileId(booker.getId()).createdByUserId(checkout.getCustomerUserId())
                .roomTypeId(checkout.getRoomTypeId()).reservationSource("CUSTOMER_PORTAL").portalRequestKey(checkout.getRequestKey())
                .reservationPolicyId(checkout.getReservationPolicyId()).guestCount(checkout.getGuestCount()).checkInDate(checkout.getCheckInDate())
                .checkOutDate(checkout.getCheckOutDate()).status(BookingStatus.PENDING.getCode()).totalPrice(checkout.getQuotedTotal()).build();
        one(bookings.insert(booking));require(booking.getId()!=null,"Failed to create paid reservation");
        var version=BookingPriceVersion.builder().bookingId(booking.getId()).versionNo(1).changeType("ORIGINAL_BOOKING")
                .reason("Accepted checkout quote").isActive(1).totalPrice(checkout.getQuotedTotal()).currency(checkout.getCurrency())
                .createdBy(checkout.getCustomerUserId()).build();one(prices.insert(version));
        var frozen=db.nights(checkout.getId());require(!frozen.isEmpty(),"Frozen nightly quote is missing");
        BigDecimal total=BigDecimal.ZERO;
        for(var n:frozen){total=total.add(n.getRateAmount());one(nights.insert(BookingNightlyRate.builder().priceVersionId(version.getId()).bookingId(booking.getId()).stayDate(n.getStayDate()).roomTypeId(n.getRoomTypeId()).rateAmount(n.getRateAmount()).rateSource(n.getRateSource()).dynamicPolicyId(n.getDynamicPolicyId()).manualOverrideId(n.getManualOverrideId()).build()));}
        require(total.compareTo(checkout.getQuotedTotal())==0,"Frozen nightly quote total mismatch");
        var account=depositService.openForReservation(booking.getId());
        one(db.linkBooking(attemptId,booking.getId()));
        // Pre-V45 successful attempts may legitimately have no recognition date. New
        // successes always receive one atomically in PaymentRecognitionService.
        var receipt=DepositPayment.builder().accountId(account.getId()).amount(attempt.getAmount()).paymentMethod(attempt.getProvider())
                .referenceNo(String.valueOf(attempt.getId())).requestKey(attempt.getRequestKey()).receivedBy(checkout.getCustomerUserId()).receivedTime(LocalDateTime.now(clock)).businessDate(attempt.getRecognizedBusinessDate()).build();
        one(deposits.receive(receipt));
        one(db.insertTransaction(HotelTransactionRecord.builder().direction("EXTERNAL_IN").amount(receipt.getAmount()).currency(checkout.getCurrency())
                .sourceType("DEPOSIT_PAYMENT").sourceId(receipt.getId()).businessReferenceType("BOOKING").businessReferenceId(booking.getId())
                .paymentMethod(attempt.getProvider()).channel("PAYMENT_PROVIDER").provider(attempt.getProvider()).providerTransactionId(attempt.getProviderPaymentId())
                .initiatorType("CUSTOMER").operatorUserId(null).description("External reservation deposit confirmed")
                .occurredAt(attempt.getCompletedAt()==null?LocalDateTime.now(clock):attempt.getCompletedAt()).businessDate(attempt.getRecognizedBusinessDate()).build()));
        one(audits.insert(SysAuditLog.builder().operatorId(checkout.getCustomerUserId()).targetUserId(checkout.getCustomerUserId()).action("EXTERNAL_RESERVATION_PAYMENT_FULFILLED").detail("Attempt "+attemptId+", booking "+booking.getId()+", deposit "+receipt.getId()).build()));
        one(db.fulfilled(attemptId,booking.getId()));db.checkoutStatus(checkout.getId(),"PAYMENT_PENDING","FULFILLED");
        return booking.getId();
    }
}
