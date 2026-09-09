package com.epms.exception;

/**
 * Raised when a request is well-formed but violates a documented Epic 4
 * business rule (e.g. duplicate royalty calculation, finalized report
 * modification, payment without approval). Maps to HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
