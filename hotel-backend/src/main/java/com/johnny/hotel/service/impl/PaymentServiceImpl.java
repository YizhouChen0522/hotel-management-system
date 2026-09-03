package com.johnny.hotel.service.impl;

import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.entity.Folio;
import com.johnny.hotel.entity.Payment;
import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.BookingMapper;
import com.johnny.hotel.mapper.FolioMapper;
import com.johnny.hotel.mapper.PaymentMapper;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import com.johnny.hotel.service.FolioFinancialService;
import com.johnny.hotel.service.FolioService;
import com.johnny.hotel.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl
        implements PaymentService {

    private static final Set<String>
            SUPPORTED_PAYMENT_METHODS =
            Set.of(
                    "CASH",
                    "CREDIT_CARD",
                    "DEBIT_CARD",
                    "ONLINE",
                    "OTHER"
            );

    private final BookingMapper bookingMapper;

    private final FolioMapper folioMapper;

    private final PaymentMapper paymentMapper;

    private final FolioService folioService;

    private final FolioFinancialService
            folioFinancialService;

    private final SysAuditLogMapper
            sysAuditLogMapper;

    @Override
    @Transactional
    public Payment recordPayment(
            Long bookingId,
            RecordPaymentRequest request,
            Long operatorId) {

        if (request == null) {
            throw new BusinessException(
                    "Payment request cannot be null"
            );
        }

        if (request.getAmount() == null
                || request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(
                    "Payment amount must be greater than zero"
            );
        }

        String paymentMethod =
                normalizePaymentMethod(
                        request.getPaymentMethod()
                );

        folioService.ensureFolioExists(
                bookingId
        );

        Folio folio =
                folioMapper
                        .selectByBookingIdForUpdate(
                                bookingId
                        );

        if (folio == null) {
            throw new BusinessException(
                    "Folio does not exist"
            );
        }

        if ("VOID".equals(
                folio.getStatus())) {

            throw new BusinessException(
                    "Cannot record payment for a void folio"
            );
        }

        Booking booking =
                bookingMapper.selectById(
                        bookingId
                );

        if (booking == null) {
            throw new BusinessException(
                    "Booking does not exist"
            );
        }

        LocalDateTime paidTime =
                LocalDateTime.now();

        Payment payment =
                Payment.builder()
                        .folioId(
                                folio.getId()
                        )
                        .amount(
                                request.getAmount()
                        )
                        .paymentMethod(
                                paymentMethod
                        )
                        .status(
                                "SUCCESS"
                        )
                        .referenceNo(
                                normalizeNullable(
                                        request.getReferenceNo()
                                )
                        )
                        .note(
                                normalizeNullable(
                                        request.getNote()
                                )
                        )
                        .createdBy(
                                operatorId
                        )
                        .paidTime(
                                paidTime
                        )
                        .build();

        int inserted =
                paymentMapper.insert(
                        payment
                );

        if (inserted != 1
                || payment.getId() == null) {

            throw new BusinessException(
                    "Failed to record payment"
            );
        }

        /*
         * 不做：
         *
         * folio.paidAmount += amount
         *
         * 而是重新 SUM 所有 SUCCESS Payment。
         */
        folioFinancialService
                .recalculateSummary(
                        bookingId
                );

        /*
         * Payment 属于敏感财务动作，
         * 记录 Audit。
         */
        sysAuditLogMapper.insert(
                SysAuditLog.builder()
                        .operatorId(
                                operatorId
                        )
                        .targetUserId(
                                booking.getUserId()
                        )
                        .action(
                                "RECORD_PAYMENT"
                        )
                        .detail(
                                "Booking "
                                        + bookingId
                                        + ", folioId "
                                        + folio.getId()
                                        + ", paymentId "
                                        + payment.getId()
                                        + ", amount "
                                        + payment.getAmount()
                                        + ", method "
                                        + paymentMethod
                        )
                        .build()
        );

        return payment;
    }

    private String normalizePaymentMethod(
            String paymentMethod) {

        if (paymentMethod == null
                || paymentMethod.isBlank()) {

            throw new BusinessException(
                    "Payment method is required"
            );
        }

        String normalized =
                paymentMethod
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!SUPPORTED_PAYMENT_METHODS
                .contains(normalized)) {

            throw new BusinessException(
                    "Unsupported payment method"
            );
        }

        return normalized;
    }

    private String normalizeNullable(
            String value) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }
}
