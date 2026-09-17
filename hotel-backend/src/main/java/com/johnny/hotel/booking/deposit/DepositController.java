package com.johnny.hotel.booking.deposit;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.pagination.PageResult;
import com.johnny.hotel.wallet.RefundRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/bookings/{bookingId}/deposit")
@PreAuthorize("hasAnyRole('CUSTOMER','STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
public class DepositController {
    private final DepositService service;
    @GetMapping public Result<DepositLedgerRules.Balance> summary(@PathVariable Long bookingId) {return Result.success(service.summary(bookingId));}
    @GetMapping("/payments") public Result<PageResult<DepositPayment>> payments(@PathVariable Long bookingId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {return Result.success(service.payments(bookingId,page,pageSize));}
    @PostMapping("/payments") @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<DepositPayment> receive(@PathVariable Long bookingId,@Valid @RequestBody DepositRequests.Receive request) {return Result.success(service.receive(bookingId,request));}
    @GetMapping("/refunds") public Result<PageResult<DepositRefund>> refunds(@PathVariable Long bookingId,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {return Result.success(service.refunds(bookingId,page,pageSize));}
    @PostMapping("/refunds") @PreAuthorize("hasRole('CUSTOMER')")
    public Result<DepositRefund> request(@PathVariable Long bookingId,@Valid @RequestBody RefundRequests.Create request) {return Result.success(service.requestRefund(bookingId,request));}
    @PostMapping("/refunds/{id}/confirm") @PreAuthorize("hasAnyRole('FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<DepositRefund> confirm(@PathVariable Long bookingId,@PathVariable Long id,@Valid @RequestBody RefundRequests.Process request) {return Result.success(service.processRefund(bookingId,id,request,true));}
    @PostMapping("/refunds/{id}/fail") @PreAuthorize("hasAnyRole('FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<DepositRefund> fail(@PathVariable Long bookingId,@PathVariable Long id,@Valid @RequestBody RefundRequests.Process request) {return Result.success(service.processRefund(bookingId,id,request,false));}
}
