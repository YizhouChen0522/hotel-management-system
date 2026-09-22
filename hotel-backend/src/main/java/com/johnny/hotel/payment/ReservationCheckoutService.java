package com.johnny.hotel.payment;

import com.johnny.hotel.enums.RoomTypeStatus;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.RoomTypeMapper;
import com.johnny.hotel.reservation.ReservationPolicyService;
import com.johnny.hotel.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class ReservationCheckoutService {
    private final PaymentCoreMapper db;
    private final PricingService pricing;
    private final ReservationPolicyService policies;
    private final RoomTypeMapper roomTypes;
    private final Clock clock;
    @Value("${hotel.payments.checkout-ttl-minutes:15}") private long ttlMinutes;
    @Value("${hotel.currency}") private String currency;

    @Transactional
    public ReservationCheckoutSession create(PaymentRequests.Checkout request,Long customerId){
        require(request.getCheckOutDate().isAfter(request.getCheckInDate()),"Check-out date must be after check-in date");
        require(!request.getCheckInDate().isBefore(LocalDate.now(clock)),"Historical bookings are not supported");
        require(ttlMinutes>0&&ttlMinutes<=1440,"Checkout TTL configuration is invalid");
        one(db.ensureCheckoutLock(customerId,request.getRequestKey()));
        require(request.getRequestKey().equals(db.lockCheckoutKey(customerId,request.getRequestKey())),"Checkout request lock failed");
        var existing=db.checkoutByKey(customerId,request.getRequestKey());
        if(existing!=null){same(existing,request);return existing;}
        var roomType=roomTypes.selectById(request.getRoomTypeId());
        require(roomType!=null&&roomType.getStatus()== RoomTypeStatus.ENABLED.getCode(),"Room type does not exist or is disabled");
        require(request.getGuestCount()<=roomType.getCapacity(),"Guest count exceeds room type capacity");
        var quote=pricing.quoteRoomType(request.getRoomTypeId(),request.getCheckInDate(),request.getCheckOutDate());
        var policy=policies.activeForBooking(false);
        var session=ReservationCheckoutSession.builder().customerUserId(customerId).roomTypeId(request.getRoomTypeId())
                .checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate()).guestCount(request.getGuestCount())
                .currency(currency).quotedTotal(money(quote.getTotalPrice(),12)).reservationPolicyId(policy==null?null:policy.getId())
                .requestKey(request.getRequestKey()).status("OPEN").expiresAt(LocalDateTime.now(clock).plusMinutes(ttlMinutes)).build();
        one(db.insertCheckout(session));
        quote.getNightlyRates().forEach(n->one(db.insertNight(CheckoutNightlyQuote.builder().checkoutSessionId(session.getId())
                .stayDate(n.getStayDate()).roomTypeId(n.getRoomTypeId()).rateAmount(n.getPrice()).rateSource(n.getRateSource())
                .dynamicPolicyId(n.getDynamicPolicyId()).manualOverrideId(n.getManualOverrideId()).build())));
        return db.lockCheckout(session.getId());
    }
    public ReservationCheckoutSession get(Long id,Long customerId){var value=db.checkout(id);if(value==null||!customerId.equals(value.getCustomerUserId()))throw new BusinessException(404,"Checkout session not found");return value;}
    private void same(ReservationCheckoutSession x,PaymentRequests.Checkout r){require(x.getRoomTypeId().equals(r.getRoomTypeId())&&x.getGuestCount().equals(r.getGuestCount())&&x.getCheckInDate().equals(r.getCheckInDate())&&x.getCheckOutDate().equals(r.getCheckOutDate()),"Request key already represents another checkout");}
}
