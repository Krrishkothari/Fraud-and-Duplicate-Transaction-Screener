package com.txnscreen.domain;

public record Flag(String transactionId, int lineNumber, FlagType type, String detail) {
}
