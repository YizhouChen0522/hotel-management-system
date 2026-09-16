package com.johnny.hotel.invoice;import com.johnny.hotel.common.Result;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequiredArgsConstructor public class InvoiceController{private final InvoiceService service;
 @GetMapping("/api/invoices/{id}")@PreAuthorize("isAuthenticated()")public Result<InvoiceView> get(@PathVariable Long id){return Result.success(service.get(id));}
 @GetMapping("/api/folios/{folioId}/invoices")@PreAuthorize("isAuthenticated()")public Result<List<InvoiceView>> list(@PathVariable Long folioId){return Result.success(service.byFolio(folioId));}
 @PostMapping("/api/admin/billing/folios/{folioId}/invoices")@PreAuthorize("hasAnyRole('FINANCE','MANAGER','OWNER','SUPER_ADMIN')")public Result<InvoiceView> issue(@PathVariable Long folioId,@Valid @RequestBody InvoiceRequests.Issue r){return Result.success(service.issue(folioId,r));}
 @PostMapping("/api/admin/billing/invoices/{id}/void")@PreAuthorize("hasAnyRole('FINANCE','MANAGER','OWNER','SUPER_ADMIN')")public Result<InvoiceView> voidInvoice(@PathVariable Long id,@Valid @RequestBody InvoiceRequests.Void r){return Result.success(service.voidInvoice(id,r));}
}
