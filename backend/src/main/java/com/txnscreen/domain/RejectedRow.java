package com.txnscreen.domain;

public record RejectedRow(int lineNumber, RejectReason reason, String raw) {
}
