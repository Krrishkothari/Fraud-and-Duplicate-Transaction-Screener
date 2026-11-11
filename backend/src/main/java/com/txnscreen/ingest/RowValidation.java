package com.txnscreen.ingest;

import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * The outcome of validating one CSV record: a transaction or a rejected row.
 */
public sealed interface RowValidation {

    /**
     * A record that passed every rule.
     */
    record Valid(Transaction transaction) implements RowValidation {
    }

    /**
     * A record that failed a rule.
     */
    record Rejected(RejectedRow row) implements RowValidation {
    }
}
