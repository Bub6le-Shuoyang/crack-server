package com.bub6le.crackserver.exception;

import lombok.Getter;

/**
 * 自定义业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private final String code;
    private final String message;

    public BusinessException(String message) {
        super(message);
        this.code = "INTERNAL_ERROR";
        this.message = message;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
