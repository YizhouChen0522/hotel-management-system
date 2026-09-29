package com.johnny.hotel.wallet;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customer-facing refund facade. Processing remains available only on the PMS surface. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@RequestMapping("/api/public/customer/folios/{folioId}/refunds")
public class PublicRefundController {
    private final RefundService service;

    @GetMapping
    public Result<List<RefundVO>> list(@PathVariable Long folioId) {
        return Result.success(service.list(folioId));
    }

    @PostMapping
    public Result<RefundVO> create(@PathVariable Long folioId,
                                   @Valid @RequestBody RefundRequests.Create request) {
        return Result.success(service.create(folioId, request));
    }
}
