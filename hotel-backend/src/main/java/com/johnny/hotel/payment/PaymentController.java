package com.johnny.hotel.payment;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import java.util.*;

@RestController @RequestMapping("/api/payments") @RequiredArgsConstructor
public class PaymentController {
    private final ReservationCheckoutService checkouts;
    private final PaymentAttemptService attempts;
    private final PaymentWebhookService webhooks;
    @PostMapping("/reservation-checkouts") @PreAuthorize("hasRole('CUSTOMER')")
    public Result<ReservationCheckoutSession> checkout(@Valid @RequestBody PaymentRequests.Checkout request,Authentication auth){return Result.success(checkouts.create(request,(Long)auth.getDetails()));}
    @GetMapping("/reservation-checkouts/{id}") @PreAuthorize("hasRole('CUSTOMER')")
    public Result<ReservationCheckoutSession> checkout(@PathVariable Long id,Authentication auth){return Result.success(checkouts.get(id,(Long)auth.getDetails()));}
    @PostMapping("/reservation-checkouts/{id}/attempts") @PreAuthorize("hasRole('CUSTOMER')")
    public Result<PaymentAttempt> attempt(@PathVariable Long id,@Valid @RequestBody PaymentRequests.Attempt request,Authentication auth){return Result.success(attempts.create(id,request,(Long)auth.getDetails()));}
    @GetMapping("/attempts/{id}") @PreAuthorize("hasRole('CUSTOMER')")
    public Result<PaymentAttempt> attempt(@PathVariable Long id,Authentication auth){return Result.success(attempts.get(id,(Long)auth.getDetails()));}
    @PostMapping("/webhooks/{provider}")
    public Result<Void> webhook(@PathVariable String provider,@RequestHeader HttpHeaders headers,@RequestBody byte[] rawBody){
        Map<String,List<String>> copy=new LinkedHashMap<>();headers.forEach((key,value)->copy.put(key,List.copyOf(value)));
        webhooks.receive(provider,new RawPaymentWebhook(copy,rawBody));return Result.success(null);
    }
}
