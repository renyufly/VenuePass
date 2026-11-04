package com.venuepass.handler;

import com.venuepass.common.BusinessException;
import com.venuepass.common.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> business(BusinessException exception) { return Result.error(exception.getMessage()); }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> duplicate(DuplicateKeyException exception) { return Result.error("Already exists"); }
}
