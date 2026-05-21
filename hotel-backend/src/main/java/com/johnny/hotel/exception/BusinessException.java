package com.johnny.hotel.exception;

public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() {
        return code;
    }

}
