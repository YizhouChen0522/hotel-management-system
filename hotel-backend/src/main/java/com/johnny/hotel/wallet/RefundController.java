package com.johnny.hotel.wallet;
import com.johnny.hotel.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
@RequestMapping("/api/folios/{folioId}/refunds")
public class RefundController {
    private final RefundService service;
    @GetMapping public Result<List<RefundVO>> list(@PathVariable Long folioId){return Result.success(service.list(folioId));}
    @PostMapping public Result<RefundVO> create(@PathVariable Long folioId,@Valid @RequestBody RefundRequests.Create request){return Result.success(service.create(folioId,request));}
    @PostMapping("/{id}/confirm") public Result<RefundVO> confirm(@PathVariable Long folioId,@PathVariable Long id,@Valid @RequestBody RefundRequests.Process request){return Result.success(service.confirm(folioId,id,request));}
    @PostMapping("/{id}/fail") public Result<RefundVO> fail(@PathVariable Long folioId,@PathVariable Long id,@Valid @RequestBody RefundRequests.Process request){return Result.success(service.fail(folioId,id,request));}
}
