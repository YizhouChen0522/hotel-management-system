package com.johnny.hotel.businessdate;
import com.johnny.hotel.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.*;
@RestController @RequestMapping("/api/admin/business-date") @RequiredArgsConstructor
public class BusinessDateController {
 private final BusinessDateService service;
 public record View(LocalDate businessDate,String state,String hotelZone,ZonedDateTime currentWallClockTime){}
 @GetMapping @PreAuthorize("hasAnyRole('STAFF','FINANCE','MANAGER','OWNER','SUPER_ADMIN')")
 public Result<View> current(){var c=service.current();var clock=service.clock();return Result.success(new View(c.getBusinessDate(),c.getState(),clock.getZone().getId(),ZonedDateTime.now(clock)));}
}
