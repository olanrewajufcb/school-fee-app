package com.fee.app.schoolfeeapp.common.exceptions;

public class GatewayException extends RuntimeException{
  public GatewayException(String paystackError, String message) {
      super(paystackError + ": " + message);
  }
}
