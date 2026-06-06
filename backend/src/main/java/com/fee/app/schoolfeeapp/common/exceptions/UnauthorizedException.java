package com.fee.app.schoolfeeapp.common.exceptions;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException{
  public UnauthorizedException(String string) {
    super(string);
  }
}
