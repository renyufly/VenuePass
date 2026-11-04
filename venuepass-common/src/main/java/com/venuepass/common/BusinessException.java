package com.venuepass.common;

// Adapted from the source project's BaseException.
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
