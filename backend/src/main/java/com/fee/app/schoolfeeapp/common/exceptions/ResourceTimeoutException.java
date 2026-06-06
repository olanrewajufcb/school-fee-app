package com.fee.app.schoolfeeapp.common.exceptions;

public class ResourceTimeoutException extends RuntimeException{
  public ResourceTimeoutException(String string, Throwable ex) {
    super(string, ex);
  }
}
