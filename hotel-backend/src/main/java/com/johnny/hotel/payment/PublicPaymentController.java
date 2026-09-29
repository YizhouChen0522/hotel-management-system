package com.johnny.hotel.payment;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Browser-facing reservation payment facade; provider webhooks remain on their dedicated endpoint. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@RequestMapping("/api/public/customer/payments")
public class PublicPaymentController {
    private final ReservationCheckoutService checkouts;
    private final PaymentAttemptService attempts;

    @PostMapping("/reservation-checkouts")
    public Result<ReservationCheckoutSession> checkout(@Valid @RequestBody PaymentRequests.Checkout request, Authentication auth) {
        return Result.success(checkouts.create(request, (Long) auth.getDetails()));
    }
    @GetMapping("/reservation-checkouts/{id}")
    public Result<ReservationCheckoutSession> checkout(@PathVariable Long id, Authentication auth) {
        return Result.success(checkouts.get(id, (Long) auth.getDetails()));
    }
    @PostMapping("/reservation-checkouts/{id}/attempts")
    public Result<PaymentAttempt> attempt(@PathVariable Long id, @Valid @RequestBody PaymentRequests.Attempt request, Authentication auth) {
        return Result.success(attempts.create(id, request, (Long) auth.getDetails()));
    }
    @GetMapping("/attempts/{id}")
    public Result<PaymentAttempt> attempt(@PathVariable Long id, Authentication auth) {
        return Result.success(attempts.get(id, (Long) auth.getDetails()));
    }
}
