package com.johnny.hotel.businessdate;

import com.johnny.hotel.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HotelTimeContextController {
    private final HotelTimeContextService service;

    public record PublicView(String zoneId, java.time.LocalDate hotelDate,
                             java.time.ZonedDateTime hotelDateTime) {}

    @GetMapping("/api/admin/time-context")
    @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<HotelTimeContextService.View> staff() {
        return Result.success(service.current());
    }

    @GetMapping("/api/public/time-context")
    public Result<PublicView> publicTime() {
        var view = service.current();
        return Result.success(new PublicView(view.zoneId(), view.hotelDate(), view.hotelDateTime()));
    }
}
