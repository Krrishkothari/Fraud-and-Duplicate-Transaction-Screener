package com.txnscreen.api;

/**
 * One failed validation rule on a request field, reported in the details of an INVALID_CONFIG error.
 */
public record FieldViolation(String field, String message) {
}
