package com.johnny.hotel.reservation;

import com.johnny.hotel.booking.deposit.*;
import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/reservations")
public class PreArrivalController {
    private final StaffDirectService direct;
    private final ReservationLifecycleService lifecycle;
    private final ReservationLifecycleMapper facts;
    private final ReservationAccess access;
    private final com.johnny.hotel.mapper.BookingMapper bookings;
    @Data @NoArgsConstructor public static class CancelRequest {
        @NotBlank private String initiator;
        @NotBlank @Size(max=500) private String reason;
    }
    @Data @NoArgsConstructor public static class ReasonRequest {
        @NotBlank @Size(max=500) private String reason;
    }
    @Data @NoArgsConstructor public static class ExternalRefundRequest {
        @NotBlank @Size(max=100) private String externalReference;
    }
    @PostMapping("/staff-direct") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<StaffDirectService.Created> create(@Valid @RequestBody StaffDirectRequests.Create request){return Result.success(direct.create(request));}
    @PostMapping("/{bookingId}/cancel") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<ReservationCancellation> cancel(@PathVariable Long bookingId,@Valid @RequestBody CancelRequest request){
        return Result.success(lifecycle.cancel(bookingId,access.hotel().id(),request.getInitiator(),request.getReason()));
    }
    @GetMapping("/{bookingId}/cancellation") @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<ReservationCancellation> cancellation(@PathVariable Long bookingId){
        var actor=access.actor();var b=bookings.selectById(bookingId);
        if(b==null||actor.customer()&&!actor.id().equals(b.getUserId())||!actor.customer()&&!actor.policyReader())
            throw new com.johnny.hotel.exception.BusinessException(404,"Reservation not found");
        return Result.success(facts.cancellation(bookingId));
    }
    @PostMapping("/{bookingId}/no-show") @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<ReservationNoShow> noShow(@PathVariable Long bookingId,@Valid @RequestBody ReasonRequest request){
        return Result.success(lifecycle.noShow(bookingId,access.hotel().id(),request.getReason()));
    }
    @PostMapping("/{bookingId}/deposit-settlements/{id}/confirm-external-refund")
    @PreAuthorize("hasAnyRole('FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<DepositSettlement> confirmExternal(@PathVariable Long bookingId,@PathVariable Long id,
                                                      @Valid @RequestBody ExternalRefundRequest request){
        return Result.success(lifecycle.confirmExternalRefund(bookingId,id,access.actor().id(),request.getExternalReference()));
    }
}
