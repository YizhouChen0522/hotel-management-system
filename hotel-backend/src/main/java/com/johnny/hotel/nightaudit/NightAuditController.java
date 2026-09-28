package com.johnny.hotel.nightaudit;
import com.johnny.hotel.common.Result;import com.johnny.hotel.pagination.PageResult;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.*;import org.springframework.format.annotation.DateTimeFormat;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.time.LocalDate;
@RestController @RequestMapping("/api/admin/night-audit") @RequiredArgsConstructor
public class NightAuditController {
 private final NightAuditService service;public record Execute(@NotBlank @Size(max=100)String requestKey){}
 private Long actor(Authentication auth){return (Long)auth.getDetails();}
 @GetMapping("/precheck") @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')") public Result<NightAuditService.Precheck> precheck(Authentication auth){return Result.success(service.precheck(actor(auth)));}
 @GetMapping @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')") public Result<NightAuditService.View> status(Authentication auth){return Result.success(service.status(actor(auth)));}
 @GetMapping("/history") @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')") public Result<PageResult<NightAuditRun>> history(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@RequestParam(required=false)String status,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize,Authentication auth){return Result.success(service.history(actor(auth),from,to,status,page,pageSize));}
 @PostMapping("/execute") @PreAuthorize("hasAnyRole('MANAGER','OWNER','SUPER_ADMIN')") public Result<NightAuditService.View> execute(@Valid @RequestBody Execute request,Authentication auth){return Result.success(service.execute(request.requestKey(),actor(auth)));}
}
