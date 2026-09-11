package com.johnny.hotel.wallet;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wallets")
@PreAuthorize("isAuthenticated()")
public class WalletController {
    private final WalletService service;
    @GetMapping("/me") public Result<WalletViews.Account> mine() {return Result.success(service.mine());}
    @GetMapping("/by-user/{userId}") public Result<WalletViews.Account> byUser(@PathVariable Long userId) {return Result.success(service.byUser(userId));}
    @GetMapping("/{id}") public Result<WalletViews.Account> get(@PathVariable Long id) {return Result.success(service.get(id));}
    @GetMapping("/{id}/transactions") public Result<List<WalletViews.Transaction>> transactions(@PathVariable Long id,@RequestParam(defaultValue="0") long after) {return Result.success(service.transactions(id,after));}
    @GetMapping("/{id}/top-ups") public Result<List<WalletViews.TopUp>> topUps(@PathVariable Long id,@RequestParam(defaultValue="0") long after) {return Result.success(service.topUps(id,after));}
    @PostMapping("/{id}/top-ups") public Result<WalletViews.TopUp> create(@PathVariable Long id,@Valid @RequestBody WalletRequests.TopUp request) {return Result.success(service.createTopUp(id,request));}
    @PostMapping("/{id}/top-ups/{topUpId}/confirm") public Result<WalletViews.TopUp> confirm(@PathVariable Long id,@PathVariable Long topUpId,@Valid @RequestBody WalletRequests.Decision request) {return Result.success(service.confirm(id,topUpId,request));}
    @PostMapping("/{id}/top-ups/{topUpId}/reject") public Result<WalletViews.TopUp> reject(@PathVariable Long id,@PathVariable Long topUpId,@Valid @RequestBody WalletRequests.Decision request) {return Result.success(service.reject(id,topUpId,request));}
    @PutMapping("/{id}/status") public Result<WalletViews.Account> status(@PathVariable Long id,@Valid @RequestBody WalletRequests.Status request) {return Result.success(service.status(id,request));}
}
