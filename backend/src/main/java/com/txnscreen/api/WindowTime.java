package com.txnscreen.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

/**
 * Reads a window bound given as an ISO-8601 timestamp with offset, such as 2026-01-05T09:00:00+05:30, or as
 * whole epoch seconds.
 */
final class WindowTime {

    private static final Pattern EPOCH_SECONDS = Pattern.compile("-?\\d{1,18}");

    private WindowTime() {
    }

    /**
     * Parses one window bound.
     *
     * @param value the parameter value
     * @param parameter the parameter name, reported when the value is invalid
     * @return the bound in epoch seconds
     * @throws ApiException 400 INVALID_WINDOW if the value is neither form
     */
    static long parse(String value, String parameter) {
        String trimmed = value.trim();
        if (EPOCH_SECONDS.matcher(trimmed).matches()) {
            return Long.parseLong(trimmed);
        }
        // A '+' in an unencoded query string arrives as a space, and a valid timestamp never has a space.
        try {
            return OffsetDateTime.parse(trimmed.replace(' ', '+')).toEpochSecond();
        } catch (DateTimeParseException e) {
            throw new ApiException("INVALID_WINDOW", HttpStatus.BAD_REQUEST,
                    "Parameter '" + parameter + "' must be an ISO-8601 timestamp with offset or epoch seconds.",
                    Map.of("parameter", parameter, "value", value));
        }
    }
}
