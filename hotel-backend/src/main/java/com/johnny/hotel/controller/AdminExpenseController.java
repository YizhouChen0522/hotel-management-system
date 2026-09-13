package com.johnny.hotel.controller;
import com.johnny.hotel.common.Result;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.service.*;
import com.johnny.hotel.vo.ExpenseVO;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/billing/folios/{folioId}/expenses")
@PreAuthorize("hasAnyRole('STAFF','MANAGER','OWNER','SUPER_ADMIN')")
public class AdminExpenseController {
    private final ExpenseService service;
    private final FolioQueryService queries;
    @PostMapping
    public Result<ExpenseVO> register(@PathVariable Long folioId,@Valid @RequestBody RegisterExpenseRequest request) {return Result.success(service.register(folioId,request));}
    @GetMapping
    public Result<List<ExpenseVO>> list(@PathVariable Long folioId) {return Result.success(queries.byFolioForOperations(folioId).expenses());}
    @PostMapping("/{expenseId}/confirm")
    public Result<ExpenseVO> confirm(@PathVariable Long folioId,@PathVariable Long expenseId) {return Result.success(service.confirm(folioId,expenseId));}
    @PostMapping("/{expenseId}/cancel")
    public Result<ExpenseVO> cancel(@PathVariable Long folioId,@PathVariable Long expenseId,@Valid @RequestBody CancelExpenseRequest request) {return Result.success(service.cancel(folioId,expenseId,request));}
}
