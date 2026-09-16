package com.johnny.hotel.guest;
import com.johnny.hotel.common.Result;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/admin/guests") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')") public class AdminGuestController {private final GuestService service;private Long id(Authentication a){return (Long)a.getDetails();}
 @GetMapping public Result<List<GuestViews.Profile>> search(@RequestParam(required=false)String q,Authentication a){return Result.success(service.search(q,id(a)));}
 @PostMapping public Result<GuestViews.Profile> create(@Valid @RequestBody GuestRequests.Profile r,Authentication a){return Result.success(service.create(r,id(a)));}
 @GetMapping("/{guestId}") public Result<GuestViews.Profile> get(@PathVariable Long guestId,Authentication a){return Result.success(service.get(guestId,id(a)));}
 @PutMapping("/{guestId}") public Result<GuestViews.Profile> update(@PathVariable Long guestId,@Valid @RequestBody GuestRequests.Profile r,Authentication a){return Result.success(service.update(guestId,r,id(a)));}
 @PostMapping("/duplicates") public Result<List<GuestViews.Profile>> duplicates(@Valid @RequestBody GuestRequests.Profile r,Authentication a){return Result.success(service.duplicateCandidates(r,id(a)));}
 @GetMapping("/{guestId}/stays") public Result<com.johnny.hotel.pagination.PageResult<GuestViews.Stay>> stays(@PathVariable Long guestId,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize,Authentication a){return Result.success(service.stayPage(guestId,id(a),page,pageSize));}
}
