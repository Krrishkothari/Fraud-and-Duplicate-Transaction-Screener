package com.txnscreen.data;

/**
 * Ground truth for one generated row: whether it repeats an earlier transaction ID, and
 * whether its account is on the blacklist.
 */
public record Label(boolean duplicate, boolean blacklisted) {
}
