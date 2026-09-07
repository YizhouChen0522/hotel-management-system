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
import com.johnny.hotel.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Set<String> SUPPORTED_PAYMENT_METHODS =
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

    private final FolioFinancialService folioFinancialService;

    private final SysAuditLogMapper sysAuditLogMapper;

    @Override
    @Transactional
    public Payment recordPayment(
            Long folioId,
            RecordPaymentRequest request,
            Long operatorId) {

        if (request == null) {
            throw new BusinessException(
                    "Payment request cannot be null"
            );
        }

        BigDecimal amount =
                normalizeAmount(
                        request.getAmount()
                );

        String paymentMethod =
                normalizePaymentMethod(
                        request.getPaymentMethod()
                );

        String referenceNo =
                normalizeNullable(
                        request.getReferenceNo()
                );

        String note =
                normalizeNullable(
                        request.getNote()
                );

        String requestKey =
                normalizeRequestKey(
                        request.getIdempotencyKey()
                );

        /*
         * 1. Payment 现在真正以 Folio 为入口。
         */
        Folio folio =
                folioMapper.selectByIdForUpdate(
                        folioId
                );

        if (folio == null) {
            throw new BusinessException(
                    "Folio does not exist"
            );
        }

        /*
         * 2. 幂等检查。
         */
        Payment existing =
                paymentMapper
                        .selectByFolioIdAndRequestKey(
                                folioId,
                                requestKey
                        );

        if (existing != null) {

            boolean sameRequest =
                    existing.getAmount()
                            .compareTo(amount) == 0

                            && existing
                            .getPaymentMethod()
                            .equals(paymentMethod)

                            && Objects.equals(
                            existing.getReferenceNo(),
                            referenceNo
                    )

                            && Objects.equals(
                            existing.getNote(),
                            note
                    );

            if (!sameRequest) {
                throw new BusinessException(
                        "Idempotency key has already been used for a different payment"
                );
            }

            if (!"SUCCESS".equals(
                    existing.getStatus())) {

                throw new BusinessException(
                        "Existing payment request is not successful"
                );
            }

            /*
             * 相同付款请求重试。
             * 不重复 INSERT。
             */
            return existing;
        }

        /*
         * 3. VOID Folio 禁止收款。
         */
        if ("VOID".equals(
                folio.getStatus())) {

            throw new BusinessException(
                    "Cannot record payment for a void folio"
            );
        }
        Booking booking =
                bookingMapper.selectById(
                        folio.getBookingId()
                );

        if (booking == null) {
            throw new BusinessException(
                    "Booking associated with folio does not exist"
            );
        }

        Payment payment = Payment.builder().folioId(folioId)
                        .amount(amount)
                        .paymentMethod(paymentMethod)
                        .status("SUCCESS")
                        .referenceNo(referenceNo)
                        .requestKey(requestKey)
                        .note(note)
                        .createdBy(operatorId)
                        .paidTime(LocalDateTime.now())
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
         * 5. FolioFinancialService 目前使用 bookingId
         * 来重新找到 Folio。
         *
         * 暂时继续这样用，没有业务错误。
         *
         * 后面我们还会把它也重构成 folioId，
         * 但这一步先不要同时改太多。
         */
        folioFinancialService
                .recalculateSummary(
                        folio.getBookingId()
                );

        /*
         * 6. Audit。
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
                                "Folio "
                                        + folioId
                                        + ", bookingId "
                                        + folio.getBookingId()
                                        + ", paymentId "
                                        + payment.getId()
                                        + ", amount "
                                        + amount
                                        + ", method "
                                        + paymentMethod
                        )
                        .build()
        );

        return payment;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(
                    "Payment amount must be greater than zero"
            );
        }

        try {
            BigDecimal normalized =
                    amount.setScale(
                            2,
                            RoundingMode.UNNECESSARY
                    );

            if (normalized.precision() > 12) {
                throw new BusinessException(
                        "Payment amount exceeds the supported range"
                );
            }

            return normalized;

        } catch (ArithmeticException ex) {

            throw new BusinessException(
                    "Payment amount cannot have more than 2 decimal places"
            );
        }
    }

    private String normalizePaymentMethod(String paymentMethod) {

        if (paymentMethod == null
                || paymentMethod.isBlank()) {

            throw new BusinessException(
                    "Payment method is required"
            );
        }

        String normalized =
                paymentMethod.trim().toUpperCase(Locale.ROOT);

        if (!SUPPORTED_PAYMENT_METHODS.contains(normalized)) {
            throw new BusinessException(
                    "Unsupported payment method"
            );
        }

        return normalized;
    }

    private String normalizeRequestKey(String value) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    "Idempotency key is required"
            );
        }

        String trimmed = value.trim();

        try {
            String canonical =
                    UUID.fromString(trimmed).toString();

            if (!canonical.equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException();
            }

            return canonical;

        } catch (IllegalArgumentException ex) {

            throw new BusinessException(
                    "Idempotency key must be a valid UUID"
            );
        }
    }

    private String normalizeNullable(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}