package com.txnscreen.api;

import java.util.List;

/**
 * The JSON body for appending one live transaction. Every field is text, and a missing field is treated as
 * blank, so the usual validation rules report it.
 *
 * @param transactionId the transaction ID
 * @param accountId the account ID
 * @param amount the amount in rupees with at most 2 decimal places, such as 1234.50
 * @param timestamp an ISO-8601 timestamp with offset, such as 2026-03-14T09:26:53+05:30
 * @param merchant the merchant name
 */
public record AppendRequest(
        String transactionId,
        String accountId,
        String amount,
        String timestamp,
        String merchant) {

    /**
     * Returns the fields in the order the validator expects, with missing fields as empty text.
     *
     * @return the five fields
     */
    List<String> fields() {
        return List.of(blankIfNull(transactionId), blankIfNull(accountId), blankIfNull(amount),
                blankIfNull(timestamp), blankIfNull(merchant));
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
