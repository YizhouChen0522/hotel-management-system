package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.entity.Payment;
import com.johnny.hotel.service.PaymentService;
import com.johnny.hotel.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/folios/{folioId}/payments")
    @PreAuthorize(
            "hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')"
    )
    public Result<PaymentVO> recordPayment(
            @PathVariable Long folioId,
            @Valid @RequestBody RecordPaymentRequest request,
            Authentication authentication) {

        Long currentUserId =
                (Long) authentication.getDetails();

        Payment payment =
                paymentService.recordPayment(
                        folioId,
                        request,
                        currentUserId
                );

        return Result.success(
                PaymentVO.from(payment)
        );
    }
}
