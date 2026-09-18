package com.johnny.hotel.walkin;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/front-desk/walk-ins")
public class WalkInBookingController {
    private final WalkInBookingService service;
    @PostMapping @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
    public Result<WalkInViews.Created> create(@Valid @RequestBody WalkInRequests.Create request, Authentication authentication){
        return Result.success(service.create(request,(Long)authentication.getDetails()));
    }
}
