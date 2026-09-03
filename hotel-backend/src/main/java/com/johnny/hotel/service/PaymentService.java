package com.johnny.hotel.service;

import com.johnny.hotel.dto.RecordPaymentRequest;
import com.johnny.hotel.entity.Payment;

public interface PaymentService {

    Payment recordPayment(
            Long bookingId,
            RecordPaymentRequest request,
            Long operatorId
    );
}
