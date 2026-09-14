package com.johnny.hotel.guest;
import com.johnny.hotel.common.Result;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/guests") @RequiredArgsConstructor public class GuestController {private final GuestService service;private Long id(Authentication a){return (Long)a.getDetails();}
 @GetMapping("/me") @PreAuthorize("hasRole('CUSTOMER')") public Result<GuestViews.Profile> me(Authentication a){return Result.success(service.me(id(a)));}
 @PutMapping("/me") @PreAuthorize("hasRole('CUSTOMER')") public Result<GuestViews.Profile> saveMe(@Valid @RequestBody GuestRequests.Profile r,Authentication a){return Result.success(service.saveMe(id(a),r));}
}
