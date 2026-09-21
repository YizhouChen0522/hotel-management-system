package com.johnny.hotel.reservation;

import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/reservation-policies")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','FINANCE','OWNER','SUPER_ADMIN')")
public class ReservationPolicyController {
    private final ReservationPolicyService service;
    @GetMapping public Result<List<ReservationPolicy>> list(){return Result.success(service.list());}
    @GetMapping("/active") public Result<ReservationPolicyService.View> active(){return Result.success(service.active());}
    @GetMapping("/{id}") public Result<ReservationPolicyService.View> get(@PathVariable Long id){return Result.success(service.get(id));}
    @PostMapping @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public Result<ReservationPolicyService.View> create(@Valid @RequestBody ReservationPolicyRequests.Save request){return Result.success(service.create(request));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public Result<ReservationPolicyService.View> edit(@PathVariable Long id,@Valid @RequestBody ReservationPolicyRequests.Save request){return Result.success(service.edit(id,request));}
    @PostMapping("/{id}/validate") @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public Result<ReservationPolicyService.View> validate(@PathVariable Long id){return Result.success(service.validate(id));}
    @PostMapping("/{id}/activate") @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public Result<ReservationPolicyService.View> activate(@PathVariable Long id){return Result.success(service.activate(id));}
    @PostMapping("/active/disable") @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public Result<Void> disable(){service.disable();return Result.success();}
}
