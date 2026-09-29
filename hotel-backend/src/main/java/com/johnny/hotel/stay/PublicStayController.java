package com.johnny.hotel.stay;
import com.johnny.hotel.common.Result;import com.johnny.hotel.entity.StayRoomAssignment;import com.johnny.hotel.pagination.PageResult;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('CUSTOMER')") @RequestMapping("/api/public/customer/stays")
public class PublicStayController {private final StayQueryService queries;private final StayGuestService guests;
 @GetMapping public Result<PageResult<Stay>> list(@RequestParam(required=false)Long bookingId,@RequestParam(required=false)Integer status,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize){return Result.success(queries.page(bookingId,status,page,pageSize));}
 @GetMapping("/{id}")public Result<Stay> get(@PathVariable Long id){return Result.success(queries.get(id));}
 @GetMapping("/{id}/guests")public Result<List<StayGuestService.Entry>> guests(@PathVariable Long id){queries.get(id);return Result.success(guests.list(id));}
 @GetMapping("/{id}/assignments")public Result<List<StayRoomAssignment>> assignments(@PathVariable Long id){return Result.success(queries.assignments(id));}
}
