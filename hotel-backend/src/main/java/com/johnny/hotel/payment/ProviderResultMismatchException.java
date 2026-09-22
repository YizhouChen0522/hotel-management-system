package com.johnny.hotel.payment;

import com.johnny.hotel.exception.BusinessException;

public class ProviderResultMismatchException extends BusinessException {
    public ProviderResultMismatchException(String message){super(message);}
}
