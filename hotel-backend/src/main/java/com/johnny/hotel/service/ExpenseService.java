package com.johnny.hotel.service;
import com.johnny.hotel.dto.*;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.support.ExpenseRules;
import com.johnny.hotel.vo.ExpenseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class ExpenseService {
    private final BookingMapper bookings;
    private final FolioMapper folios;
    private final ExpenseMapper expenses;
    private final FolioService ledger;
    private final SysAuditLogMapper audits;
    private final Clock clock;
    private final com.johnny.hotel.stay.StayPlan stayPlan;
    private final com.johnny.hotel.service.support.BillingAccess billingAccess;
    private record Account(Booking booking,Folio folio) {}
    private Account lock(Long id) {
        // Immutable identity lookup only; never hold Folio then request Booking.
        Folio identity=folios.selectById(id);require(identity!=null,"Folio does not exist");
        Booking b=bookings.selectByIdForUpdate(identity.getBookingId());require(b!=null,"Booking does not exist");
        Folio f=folios.selectByIdForUpdate(id);
        require(f!=null&&f.getBookingId().equals(b.getId())&&f.getClosedTime()==null,"Folio is missing, void or finalized");
        require(b.getStatus()==com.johnny.hotel.enums.BookingStatus.CHECKED_IN.getCode(),"Expenses currently require a checked-in booking");
        require(!LocalDate.now(clock).isBefore(b.getCheckInDate())&&!LocalDate.now(clock).isAfter(stayPlan.end(b)),"Expense processing outside contracted stay dates is unsupported");
        return new Account(b,f);
    }
    private Long operator(String type) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||!(auth.getDetails() instanceof Long id))throw new AccessDeniedException("Authenticated operator is required");
        ExpenseRules.authorize(type,billingAccess.operationalAuthorities(id));
        return id;
    }
    private ExpenseRegistration find(List<ExpenseRegistration> rows,Long id) {
        var e=rows.stream().filter(r->r.getId().equals(id)).findFirst().orElse(null);require(e!=null,"Expense does not belong to this folio");return e;
    }
    private void audit(Account a,ExpenseRegistration e,Long operator,String action) {
        one(audits.insert(SysAuditLog.builder().operatorId(operator).targetUserId(a.booking().getUserId()).action(action)
                .detail("Folio "+e.getFolioId()+", expense "+e.getId()+", amount "+e.getAmount()+", reason: "+("CANCEL_EXPENSE".equals(action)?e.getCancelReason():e.getReason())).build()));
    }
    @Transactional
    public ExpenseVO register(Long folioId,RegisterExpenseRequest request) {
        require(request!=null,"Expense request is required");Long actor=operator(request.getItemType());
        Account a=lock(folioId);ExpenseRules.validate(request,a.booking(),LocalDate.now(clock),stayPlan.end(a.booking()));
        var rows=expenses.selectByFolioForUpdate(folioId);
        ExpenseRegistration e=ExpenseRegistration.builder().folioId(folioId).requestKey(ExpenseRules.key(request.getIdempotencyKey()))
                .itemType(request.getItemType()).amount(money(request.getAmount(),12)).businessDate(request.getBusinessDate())
                .description(ExpenseRules.text(request.getDescription())).reason(ExpenseRules.text(request.getReason())).sourceExpenseId(request.getSourceExpenseId())
                .status("PENDING").registeredBy(actor).registeredTime(LocalDateTime.now(clock)).build();
        var existing=rows.stream().filter(r->r.getRequestKey().equals(e.getRequestKey())).findFirst().orElse(null);
        if(existing!=null) {
            require(existing.getItemType().equals(e.getItemType())&&existing.getAmount().compareTo(e.getAmount())==0&&existing.getBusinessDate().equals(e.getBusinessDate())
                    &&existing.getDescription().equals(e.getDescription())&&existing.getReason().equals(e.getReason())&&Objects.equals(existing.getSourceExpenseId(),e.getSourceExpenseId()),"Idempotency key already used for a different expense");
            return ExpenseVO.from(existing);
        }
        ExpenseRules.sourceAndCapacity(e,rows);
        one(expenses.insert(e));require(e.getId()!=null,"Expense insert did not return an id");audit(a,e,actor,"REGISTER_EXPENSE");return ExpenseVO.from(e);
    }
    @Transactional
    public ExpenseVO confirm(Long folioId,Long expenseId) {
        operator("SERVICE_CHARGE");Account a=lock(folioId);var rows=expenses.selectByFolioForUpdate(folioId);var e=find(rows,expenseId);Long actor=operator(e.getItemType());
        if("CONFIRMED".equals(e.getStatus()))return ExpenseVO.from(e); // Stable expense ID is the confirmation idempotency key.
        require("PENDING".equals(e.getStatus()),"Only pending expenses can be confirmed");
        ExpenseRules.validate(RegisterExpenseRequest.builder().idempotencyKey(e.getRequestKey()).itemType(e.getItemType()).amount(e.getAmount())
                .businessDate(e.getBusinessDate()).description(e.getDescription()).reason(e.getReason()).sourceExpenseId(e.getSourceExpenseId()).build(),a.booking(),LocalDate.now(clock),stayPlan.end(a.booking()));
        var source=ExpenseRules.sourceAndCapacity(e,rows);
        FolioItem posted=ledger.addItem(a.booking().getId(),FolioItemCommand.builder().itemType(e.getItemType()).description(e.getDescription())
                .businessDate(e.getBusinessDate()).quantity(BigDecimal.ONE).unitPrice(e.getAmount()).amount(e.getAmount())
                .sourceItemId(source==null?null:source.getLedgerItemId()).eventKey("EXPENSE:"+e.getId()).refundable(false).build(),actor);
        e.setStatus("CONFIRMED");e.setLedgerItemId(posted.getId());e.setResolvedBy(actor);e.setResolvedTime(LocalDateTime.now(clock));
        one(expenses.resolve(e));audit(a,e,actor,"CONFIRM_EXPENSE");return ExpenseVO.from(e);
    }
    @Transactional
    public ExpenseVO cancel(Long folioId,Long expenseId,CancelExpenseRequest request) {
        operator("SERVICE_CHARGE");require(request!=null,"Cancellation request is required");String key=ExpenseRules.key(request.getIdempotencyKey()),reason=ExpenseRules.text(request.getReason());
        Account a=lock(folioId);var rows=expenses.selectByFolioForUpdate(folioId);var e=find(rows,expenseId);Long actor=operator(e.getItemType());
        if("CANCELLED".equals(e.getStatus())) {require(key.equals(e.getCancelKey())&&reason.equals(e.getCancelReason()),"Different cancellation request already resolved this expense");return ExpenseVO.from(e);}
        require("PENDING".equals(e.getStatus()),"Confirmed expenses require a new FEE_REVERSAL registration; historical charges cannot be cancelled");
        require(rows.stream().noneMatch(r->key.equals(r.getCancelKey())),"Cancellation key already used for another expense");
        e.setStatus("CANCELLED");e.setCancelKey(key);e.setCancelReason(reason);e.setResolvedBy(actor);e.setResolvedTime(LocalDateTime.now(clock));
        one(expenses.resolve(e));audit(a,e,actor,"CANCEL_EXPENSE");return ExpenseVO.from(e);
    }
}
