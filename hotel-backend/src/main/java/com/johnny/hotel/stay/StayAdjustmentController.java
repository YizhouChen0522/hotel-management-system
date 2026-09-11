package com.johnny.hotel.stay;
import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/bookings/{bookingId}/stay-adjustments")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class StayAdjustmentController {
    private final StayAdjustmentService service;
    @PostMapping public Result<StayAdjustment> apply(@PathVariable Long bookingId,@Valid @RequestBody StayAdjustmentRequest request){return Result.success(service.apply(bookingId,request));}
    @GetMapping public Result<List<StayAdjustment>> list(@PathVariable Long bookingId){return Result.success(service.list(bookingId));}
}
