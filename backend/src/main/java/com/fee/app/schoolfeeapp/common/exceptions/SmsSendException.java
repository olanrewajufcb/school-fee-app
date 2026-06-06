package com.fee.app.schoolfeeapp.common.exceptions;

/**
 * Exception for SMS send failures.
 */
public class SmsSendException extends RuntimeException {
    public SmsSendException(String message) {
        super(message);
    }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}