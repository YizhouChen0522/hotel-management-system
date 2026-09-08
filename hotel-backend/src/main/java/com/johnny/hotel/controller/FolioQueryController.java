package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.service.FolioQueryService;
import com.johnny.hotel.vo.FolioVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FolioQueryController {
    private final FolioQueryService queries;
    @GetMapping("/api/admin/billing/bookings/{bookingId}/folio")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<FolioVO> booking(@PathVariable Long bookingId) { return Result.success(queries.byBooking(bookingId, null)); }
    @GetMapping("/api/admin/billing/folios/{folioId}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<FolioVO> folio(@PathVariable Long folioId) { return Result.success(queries.byFolio(folioId, null)); }
    @GetMapping("/api/bookings/{bookingId}/folio")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Result<FolioVO> mine(@PathVariable Long bookingId, Authentication auth) {
        return Result.success(queries.byBooking(bookingId, (Long) auth.getDetails()));
    }
}
