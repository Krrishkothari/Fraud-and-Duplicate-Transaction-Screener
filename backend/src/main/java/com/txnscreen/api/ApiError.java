package com.txnscreen.api;

import java.util.Map;

/**
 * The single JSON body returned for every API error.
 */
public record ApiError(String code, String message, Map<String, Object> details) {
}
