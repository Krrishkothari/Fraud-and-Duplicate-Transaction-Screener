package com.txnscreen.ingest;

import java.util.List;

import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * The outcome of parsing a transaction CSV.
 *
 * @param valid the valid transactions, sorted by time with equal times in file order
 * @param rejected the rejected rows, in file order
 * @param totalRows the number of non-blank data rows, which is valid plus rejected
 */
public record ParseResult(List<Transaction> valid, List<RejectedRow> rejected, int totalRows) {

    public ParseResult {
        valid = List.copyOf(valid);
        rejected = List.copyOf(rejected);
    }
}
