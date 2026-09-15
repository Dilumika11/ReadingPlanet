package com.epms.exception;

/**
 * The request is well-formed but its values don't make sense together
 * (e.g. a period that ends before it starts, or lies in the future).
 * Mapped to HTTP 400, unlike {@link BusinessRuleException} (409) which is
 * for state conflicts with data that already exists.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
