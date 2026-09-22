package com.johnny.hotel.payment;

import com.johnny.hotel.common.Result;import com.johnny.hotel.pagination.PageResult;import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.time.LocalDateTime;

@RestController @RequestMapping("/api/finance/transactions") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE','OWNER','SUPER_ADMIN')")
public class HotelTransactionController {
 private final HotelTransactionQueryService service;
 @GetMapping public Result<PageResult<HotelTransactionRecord>> list(@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size,
  @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)LocalDateTime from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)LocalDateTime to,
  @RequestParam(required=false)String direction,@RequestParam(required=false)String sourceType,@RequestParam(required=false)String paymentMethod,
  @RequestParam(required=false)String provider,@RequestParam(required=false)String currency,@RequestParam(required=false)String businessReferenceType,
  @RequestParam(required=false)Long businessReferenceId,@RequestParam(required=false)String providerReference){return Result.success(service.query(page,size,from,to,direction,sourceType,paymentMethod,provider,currency,businessReferenceType,businessReferenceId,providerReference));}
}
