package com.johnny.hotel.wallet;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public customer wallet facade; employee administration remains on the PMS API. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@RequestMapping("/api/public/customer/wallet")
public class PublicWalletController {
    private final WalletService service;

    @GetMapping public Result<WalletViews.Account> mine() { return Result.success(service.mine()); }
    @GetMapping("/transactions") public Result<List<WalletViews.Transaction>> transactions(@RequestParam(defaultValue="0") long after) {
        var wallet = service.mine();
        return Result.success(service.transactions(wallet.id(), after));
    }
    @GetMapping("/top-ups") public Result<List<WalletViews.TopUp>> topUps(@RequestParam(defaultValue="0") long after) {
        var wallet = service.mine();
        return Result.success(service.topUps(wallet.id(), after));
    }
    @PostMapping("/top-ups") public Result<WalletViews.TopUp> create(@Valid @RequestBody WalletRequests.TopUp request) {
        var wallet = service.mine();
        return Result.success(service.createTopUp(wallet.id(), request));
    }
}
