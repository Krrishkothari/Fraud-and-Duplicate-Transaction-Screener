package com.txnscreen.service;

/**
 * A result together with how long the algorithm step that produced it took.
 *
 * @param value the result
 * @param algoMicros the algorithm time, in microseconds
 * @param <T> the result type
 */
public record Timed<T>(T value, long algoMicros) {
}
