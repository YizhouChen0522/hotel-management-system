package com.johnny.hotel.wallet;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.enums.RefundStatus;
import com.johnny.hotel.service.FolioFinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class RefundService {
    private final RefundMapper refunds;
    private final FolioMapper folios;
    private final BookingMapper bookings;
    private final WalletMapper wallets;
    private final WalletPostingService posting;
    private final RefundAccess access;
    private final FolioFinancialService financial;
    private final SysAuditLogMapper audits;
    private record Account(Folio folio,Booking booking) {}
    private Account lock(Long id) { return lock(id, false, null); }
    private Account lock(Long id, boolean hideExistence, Long owner) {
        var f=folios.selectByIdForUpdate(id);
        if(f==null) {if(hideExistence)throw new com.johnny.hotel.exception.BusinessException(404,"Refund resource not found");require(false,"Folio does not exist");}
        var b=bookings.selectById(f.getBookingId());
        if(b==null || hideExistence && !owner.equals(b.getUserId()))throw new com.johnny.hotel.exception.BusinessException(hideExistence?404:400,hideExistence?"Refund resource not found":"Booking does not exist");
        return new Account(f,b);
    }
    private void open(Account a) {require(a.folio().getClosedTime()==null,"Finalized or void Folio cannot acquire refund obligations");}
    private String key(String key){require(key!=null && key.matches("[A-Za-z0-9_-]{8,64}"),"Invalid request key");return key;}
    private String reason(String s){require(s!=null && !s.isBlank() && s.length()<=255,"Reason is required (255 characters maximum)");return s.trim();}
    private BigDecimal pending(List<Refund> rows){return rows.stream().filter(r->r.getStatus()==RefundStatus.PENDING.getCode()).map(Refund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private Wallet wallet(Account a) {
        var identity=wallets.byUser(a.booking().getUserId());require(identity!=null,"Beneficiary wallet is missing");
        var w=wallets.lock(identity.getId());require(w.getUserId().equals(a.booking().getUserId()) && w.getCurrency().equals(a.folio().getCurrency()),"Refund wallet ownership or currency mismatch");return w;
    }
    private void audit(Long actor,Account a,String action,Long id,String reason) {
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(a.booking().getUserId()).action(action)
                .detail("Folio "+a.folio().getId()+", refund "+id+": "+reason).build()));
    }
    @Transactional
    public List<RefundVO> list(Long folioId) {
        Long actor=access.actor();boolean customer=access.isCustomer(actor);var a=lock(folioId,customer,actor);access.read(actor,a.booking().getUserId());
        return refunds.forFolio(folioId).stream().map(RefundVO::from).toList();
    }
    @Transactional
    public RefundVO create(Long folioId,RefundRequests.Create request) {
        Long actor=access.actor();require(request!=null,"Refund request is required");String key=key(request.getRequestKey()),reason=reason(request.getReason());
        var amount=money(request.getAmount(),12);require(amount.signum()>0,"Refund amount must be positive");
        boolean customer=access.isCustomer(actor);var a=lock(folioId,customer,actor);access.create(actor,a.booking().getUserId());
        // Current reads under Folio: ledger -> payments -> refunds. Never Refund -> Folio.
        var summary=financial.recalculateSummary(a.booking().getId());var rows=refunds.forFolio(folioId);
        var existing=rows.stream().filter(r->r.getRequestKey().equals(key)).findFirst().orElse(null);
        if(existing!=null){require(existing.getAmount().compareTo(amount)==0 && existing.getReason().equals(reason),"Request key already used for another refund");return RefundVO.from(existing);}
        open(a);require(summary.getBalanceAmount().negate().subtract(pending(rows)).compareTo(amount)>=0,"Insufficient unreserved Folio credit");
        var w=wallet(a);
        var r=Refund.builder().folioId(folioId).bookingId(a.booking().getId()).userId(a.booking().getUserId()).walletId(w.getId()).currency(a.folio().getCurrency()).amount(amount)
                .requestKey(key).reason(reason).requestedBy(actor).build();
        one(refunds.insert(r));audit(actor,a,"REFUND_REQUEST",r.getId(),reason);
        return RefundVO.from(refunds.forFolio(folioId).stream().filter(row->row.getId().equals(r.getId())).findFirst().orElseThrow());
    }
    @Transactional
    public RefundVO confirm(Long folioId,Long id,RefundRequests.Process request){return process(folioId,id,request,true);}
    @Transactional
    public RefundVO fail(Long folioId,Long id,RefundRequests.Process request){return process(folioId,id,request,false);}
    private RefundVO process(Long folioId,Long id,RefundRequests.Process request,boolean success) {
        Long actor=access.actor();require(request!=null,"Processing request is required");String key=key(request.getRequestKey()),reason=reason(request.getReason());
        access.operational(actor,true);
        var a=lock(folioId);access.approve(actor,a.booking().getUserId());
        var summary=financial.recalculateSummary(a.booking().getId());var rows=refunds.forFolio(folioId);
        var r=rows.stream().filter(row->row.getId().equals(id)).findFirst().orElse(null);require(r!=null,"Refund does not belong to this Folio");
        int target=success?RefundStatus.SUCCESS.getCode():RefundStatus.FAILED.getCode();
        if(r.getStatus()!=RefundStatus.PENDING.getCode()){
            require(r.getStatus()==target && key.equals(r.getProcessKey()) && reason.equals(r.getProcessReason()),"Refund already processed with another decision");return RefundVO.from(r);
        }
        open(a);require(r.getUserId().equals(a.booking().getUserId()) && r.getCurrency().equals(a.folio().getCurrency()) && "HOTEL_WALLET".equals(r.getDestination()),"Refund identity mismatch");
        if(success) {
            require(summary.getBalanceAmount().negate().compareTo(pending(rows))>=0,"Current Folio credit no longer covers refund reservations");
            var w=wallet(a);require(w.getId().equals(r.getWalletId()),"Refund beneficiary mismatch");
            // Source remains PENDING until the append and balance delta are complete; everything commits atomically.
            posting.creditRefund(w,r,actor);
        }
        one(refunds.process(id,target,actor,key,reason));audit(actor,a,success?"REFUND_SUCCESS":"REFUND_FAILED",id,reason);
        financial.recalculateSummary(a.booking().getId());
        return RefundVO.from(refunds.forFolio(folioId).stream().filter(row->row.getId().equals(id)).findFirst().orElseThrow());
    }
}
