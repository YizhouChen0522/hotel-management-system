package com.johnny.hotel.stay;
import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/stays/{stayId}/stay-adjustments")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class StayAdjustmentController {
    private final StayAdjustmentService service;
    @PostMapping public Result<StayAdjustment> apply(@PathVariable Long stayId,@Valid @RequestBody StayAdjustmentRequest request){return Result.success(service.apply(stayId,request));}
    @GetMapping @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')") public Result<List<StayAdjustment>> list(@PathVariable Long stayId){return Result.success(service.list(stayId));}
}
