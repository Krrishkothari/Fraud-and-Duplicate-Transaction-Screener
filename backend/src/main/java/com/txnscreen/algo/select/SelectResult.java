package com.txnscreen.algo.select;

/**
 * The value Quickselect found and how many element comparisons it made to find it.
 */
public record SelectResult(long value, long comparisons) {
}
