package com.txnscreen.data;

import com.txnscreen.domain.Transaction;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * One generated dataset.
 *
 * @param rows the transactions sorted by time, with CSV line numbers starting at 2 (line 1 is the header)
 * @param blacklist the blacklisted account IDs, including some that never appear in the rows
 * @param labels the ground truth for every row, keyed by its CSV line number
 */
public record GeneratedData(List<Transaction> rows, SortedSet<String> blacklist, Map<Integer, Label> labels) {
}
