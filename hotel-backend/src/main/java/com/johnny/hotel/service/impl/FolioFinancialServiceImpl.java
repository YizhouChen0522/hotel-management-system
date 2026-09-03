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

    private final FolioMapper folioMapper;

    private final FolioItemMapper folioItemMapper;

    private final PaymentMapper paymentMapper;

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

        if ("VOID".equals(folio.getStatus())) {
            throw new BusinessException(
                    "Cannot recalculate a void folio"
            );
        }

        BigDecimal totalAmount = folioItemMapper.sumAmountByFolioId(folio.getId());

        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        BigDecimal paidAmount =
                paymentMapper.sumSuccessfulAmountByFolioId(
                        folio.getId()
                );

        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }

        BigDecimal balanceAmount = totalAmount.subtract(paidAmount);

        int itemCount =
                folioItemMapper.countByFolioId(
                        folio.getId()
                );

        String newStatus;
        LocalDateTime settledTime = null;

        if (balanceAmount.compareTo(
                BigDecimal.ZERO) < 0) {

            newStatus = "CREDIT_BALANCE";

        } else if (balanceAmount.compareTo(
                BigDecimal.ZERO) == 0
                && (itemCount > 0
                || paidAmount.compareTo(
                BigDecimal.ZERO) > 0)) {

            newStatus = "SETTLED";

            if ("SETTLED".equals(
                    folio.getStatus())
                    && folio.getSettledTime() != null) {

                settledTime =
                        folio.getSettledTime();

            } else {

                settledTime =
                        LocalDateTime.now();
            }

        } else if (paidAmount.compareTo(
                BigDecimal.ZERO) > 0) {

            newStatus =
                    "PARTIALLY_PAID";

        } else {

            newStatus =
                    "OPEN";
        }

        int updated =
                folioMapper.updateFinancialSummary(
                        folio.getId(),
                        totalAmount,
                        paidAmount,
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
        return folioMapper.selectByBookingId(
                bookingId
        );
    }
}