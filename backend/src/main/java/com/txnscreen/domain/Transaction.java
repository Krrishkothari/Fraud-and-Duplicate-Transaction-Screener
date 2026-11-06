package com.txnscreen.domain;

public record Transaction(
        int lineNumber,
        String transactionId,
        String accountId,
        long amountPaise,
        long epochSeconds,
        String merchant) {
}
