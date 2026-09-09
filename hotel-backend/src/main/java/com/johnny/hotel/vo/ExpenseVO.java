package com.johnny.hotel.vo;
import com.johnny.hotel.entity.ExpenseRegistration;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.*;

@Builder
public record ExpenseVO(Long id, Long folioId, String itemType, BigDecimal amount, LocalDate businessDate,
        String description, String reason, Long sourceExpenseId, String status, Long ledgerItemId,
        Long registeredBy, LocalDateTime registeredTime, Long resolvedBy, LocalDateTime resolvedTime, String cancelReason) {
    public static ExpenseVO from(ExpenseRegistration e) {
        return ExpenseVO.builder().id(e.getId()).folioId(e.getFolioId()).itemType(e.getItemType()).amount(e.getAmount())
                .businessDate(e.getBusinessDate()).description(e.getDescription()).reason(e.getReason()).sourceExpenseId(e.getSourceExpenseId())
                .status(e.getStatus()).ledgerItemId(e.getLedgerItemId()).registeredBy(e.getRegisteredBy()).registeredTime(e.getRegisteredTime())
                .resolvedBy(e.getResolvedBy()).resolvedTime(e.getResolvedTime()).cancelReason(e.getCancelReason()).build();
    }
}
