package com.johnny.hotel.service.support;
import com.johnny.hotel.dto.RegisterExpenseRequest;
import com.johnny.hotel.entity.*;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

public final class ExpenseRules {
    private ExpenseRules() {}
    public static boolean credit(String type) { return "DISCOUNT".equals(type) || "FEE_REVERSAL".equals(type); }
    public static String key(String value) {
        require(value!=null,"Idempotency key is required");
        try {String canonical=UUID.fromString(value.trim()).toString(); require(canonical.equalsIgnoreCase(value.trim()),"Idempotency key must be a canonical UUID");return canonical;}
        catch(IllegalArgumentException e){throw new com.johnny.hotel.exception.BusinessException("Idempotency key must be a UUID");}
    }
    public static String text(String value) {require(value!=null&&!value.isBlank()&&value.length()<=500,"Description and reason must contain 1 to 500 characters");return value.trim();}
    public static void authorize(String type, Collection<String> roles) {
        boolean manager=roles.stream().anyMatch(Set.of("ROLE_MANAGER","ROLE_OWNER","ROLE_SUPER_ADMIN")::contains);
        if(!manager && (credit(type)||!roles.contains("ROLE_STAFF"))) throw new AccessDeniedException("Insufficient expense permission");
    }
    public static void validate(RegisterExpenseRequest r, Booking b, LocalDate today) {
        require(r!=null,"Expense request is required");
        require(r.getItemType()!=null&&Set.of("SERVICE_CHARGE","DAMAGE_CHARGE","DISCOUNT","FEE_REVERSAL").contains(r.getItemType()),"Unsupported expense type");
        BigDecimal amount=money(r.getAmount(),12);
        require(credit(r.getItemType())?amount.signum()<0:amount.signum()>0,"Expense type and amount sign disagree");
        require(credit(r.getItemType())?r.getSourceExpenseId()!=null&&r.getSourceExpenseId()>0:r.getSourceExpenseId()==null,"Credits require a positive source expense; charges cannot reference a source");
        require(b.getStatus()==2,"Expenses currently require a checked-in booking");
        LocalDate date=r.getBusinessDate();
        require(date!=null&&!date.isBefore(b.getCheckInDate())&&!date.isAfter(b.getCheckOutDate())&&!date.isAfter(today),"Expense date must be an elapsed business day within the contracted stay");
        require(!today.isBefore(b.getCheckInDate())&&!today.isAfter(b.getCheckOutDate()),"Expenses outside contracted stay dates are unsupported");
        text(r.getDescription());text(r.getReason());key(r.getIdempotencyKey());
    }
    public static ExpenseRegistration sourceAndCapacity(ExpenseRegistration e,List<ExpenseRegistration> all) {
        if(!credit(e.getItemType()))return null;
        ExpenseRegistration source=all.stream().filter(s->s.getId().equals(e.getSourceExpenseId())).findFirst().orElse(null);
        require(source!=null&&source.getFolioId().equals(e.getFolioId())&&"CONFIRMED".equals(source.getStatus())&&!credit(source.getItemType())&&source.getAmount().signum()>0,"Source must be a confirmed positive expense in this folio");
        require(source.getBusinessDate().equals(e.getBusinessDate()),"Adjustment business date must match its original charge");
        BigDecimal reserved=all.stream().filter(c->source.getId().equals(c.getSourceExpenseId())&&!"CANCELLED".equals(c.getStatus())&&!Objects.equals(c.getId(),e.getId()))
                .map(ExpenseRegistration::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        require(source.getAmount().add(reserved).add(e.getAmount()).signum()>=0,"Adjustment exceeds remaining amount, including pending adjustments");
        return source;
    }
    /** Returns only verified room ledger for the unchanged room-history validator. */
    public static List<FolioItem> checkout(Booking booking,Long folioId,List<ExpenseRegistration> all,List<FolioItem> ledger) {
        Set<Long> accounted=new HashSet<>();
        for(var e:all) {
            require(e.getFolioId().equals(folioId),"Expense belongs to another folio");
            require(!"PENDING".equals(e.getStatus()),"Pending expenses or adjustments must be confirmed or cancelled before checkout");
            if("CANCELLED".equals(e.getStatus())) {require(e.getLedgerItemId()==null&&e.getCancelReason()!=null&&e.getResolvedBy()!=null,"Invalid cancelled expense");continue;}
            require("CONFIRMED".equals(e.getStatus())&&e.getLedgerItemId()!=null&&e.getResolvedBy()!=null&&e.getResolvedTime()!=null,"Unresolved or incomplete expense");
            var request=RegisterExpenseRequest.builder().idempotencyKey(e.getRequestKey()).itemType(e.getItemType()).amount(e.getAmount()).businessDate(e.getBusinessDate()).description(e.getDescription()).reason(e.getReason()).sourceExpenseId(e.getSourceExpenseId()).build();
            validate(request,booking,booking.getCheckOutDate());
            var source=sourceAndCapacity(e,all);
            FolioItem item=ledger.stream().filter(i->i.getId().equals(e.getLedgerItemId())).findFirst().orElse(null);
            require(item!=null&&accounted.add(item.getId())&&folioId.equals(item.getFolioId())&&e.getItemType().equals(item.getItemType())
                    &&e.getAmount().compareTo(item.getAmount())==0&&e.getBusinessDate().equals(item.getBusinessDate())&&e.getDescription().equals(item.getDescription())
                    &&item.getQuantity().compareTo(BigDecimal.ONE)==0&&item.getUnitPrice().compareTo(e.getAmount())==0
                    &&("EXPENSE:"+e.getId()).equals(item.getEventKey())&&e.getResolvedBy().equals(item.getCreatedBy())
                    &&item.getRoomAssignmentId()==null&&item.getRoomId()==null&&item.getRoomTypeId()==null
                    &&Objects.equals(item.getSourceItemId(),source==null?null:source.getLedgerItemId()),"Confirmed expense does not match posted ledger");
        }
        var remainder=ledger.stream().filter(i->!accounted.contains(i.getId())).toList();
        require(remainder.stream().allMatch(i->Set.of("ROOM_CHARGE","ROOM_RATE_ADJUSTMENT").contains(i.getItemType())),"Unregistered or unmatched extra fee ledger");
        return remainder;
    }
}
