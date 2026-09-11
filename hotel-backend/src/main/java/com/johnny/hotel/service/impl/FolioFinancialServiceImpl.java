package com.johnny.hotel.service.impl;

import com.johnny.hotel.entity.Folio;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.FolioItemMapper;
import com.johnny.hotel.mapper.FolioMapper;
import com.johnny.hotel.mapper.PaymentMapper;
import com.johnny.hotel.service.FolioFinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FolioFinancialServiceImpl implements FolioFinancialService {

    private final java.time.Clock clock;
    private final FolioMapper folioMapper;

    private final FolioItemMapper folioItemMapper;

    private final PaymentMapper paymentMapper;
    private final com.johnny.hotel.wallet.RefundMapper refundMapper;

    @Override
    @Transactional
    public Folio recalculateSummary(
            Long bookingId) {

        Folio folio =
                folioMapper.selectByBookingIdForUpdate(
                        bookingId
                );

        if (folio == null) {
            throw new BusinessException(
                    "Folio does not exist"
            );
        }

        // Lock the account first, then read current ledger rows (not RR snapshot SUMs).
        var items = folioItemMapper.selectByFolioIdForUpdate(folio.getId());
        var payments = paymentMapper.selectByFolioIdForUpdate(folio.getId());
        BigDecimal totalAmount = items.stream().map(com.johnny.hotel.entity.FolioItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        BigDecimal paidAmount = payments.stream().filter(p -> "SUCCESS".equals(p.getStatus()))
                .map(com.johnny.hotel.entity.Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }

        BigDecimal refundedAmount = refundMapper.forFolio(folio.getId()).stream()
                .filter(r -> r.getStatus() == com.johnny.hotel.enums.RefundStatus.SUCCESS.getCode())
                .map(com.johnny.hotel.wallet.Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceAmount = totalAmount.subtract(paidAmount).add(refundedAmount);

        int newStatus = com.johnny.hotel.enums.FolioStatus.forBalance(balanceAmount).getCode();
        LocalDateTime settledTime = newStatus == 1
                ? (Integer.valueOf(1).equals(folio.getStatus()) && folio.getSettledTime()!=null ? folio.getSettledTime() : LocalDateTime.now(clock)) : null;

        com.johnny.hotel.service.support.BillingRules.money(totalAmount, 12);
        com.johnny.hotel.service.support.BillingRules.money(paidAmount, 12);
        com.johnny.hotel.service.support.BillingRules.money(refundedAmount, 12);
        com.johnny.hotel.service.support.BillingRules.money(balanceAmount, 12);
        if (folio.getClosedTime() != null && (newStatus != 1 || balanceAmount.signum() != 0))
            throw new BusinessException("Closed folio financial integrity violation");
        int updated =
                folioMapper.updateFinancialSummary(
                        folio.getId(),
                        totalAmount,
                        paidAmount,
                        refundedAmount,
                        balanceAmount,
                        newStatus,
                        settledTime
                );

        if (updated != 1) {
            throw new BusinessException(
                    "Failed to update folio financial summary"
            );
        }

        /*
         * 返回数据库中的最新状态，
         * 而不是返回内存里那个旧 folio 对象。
         */
        return folioMapper.selectByBookingIdForUpdate(
                bookingId
        );
    }
}
