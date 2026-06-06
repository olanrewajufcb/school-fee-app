package com.fee.app.schoolfeeapp.common.exceptions;

import lombok.Getter;

@Getter
public class SchoolFeeException extends RuntimeException {
    private final String errorCode;
    private final String field;
    
    public SchoolFeeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }
    
    public SchoolFeeException(String errorCode, String message, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public SchoolFeeException(String errorCode, String message, String field, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.field = field;
    }

    public SchoolFeeException(String message, Throwable cause) {
        super(message, cause);
        this.field = null;
        this.errorCode = null;
    }
}
