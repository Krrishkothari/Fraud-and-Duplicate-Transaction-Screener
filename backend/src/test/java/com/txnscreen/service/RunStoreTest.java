package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RunStoreTest {

    private static final Instant START = Instant.parse("2026-11-12T10:00:00Z");

    private final MovableClock clock = new MovableClock(START);
    private final RunStore store = new RunStore(clock);

    // Proves the store keeps at most 10 runs and evicts the least recently used one, not the oldest.
    @Test
    void evictsLeastRecentlyUsedRun() {
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < RunStore.MAX_RUNS; i++) {
            runs.add(run(START));
            store.save(runs.get(i));
        }
        store.find(runs.get(0).id());

        Run newest = run(START);
        store.save(newest);

        assertAll(
                () -> assertEquals(RunStore.MAX_RUNS, store.size()),
                () -> assertTrue(store.find(runs.get(0).id()).isPresent(), "recently read run is kept"),
                () -> assertTrue(store.find(runs.get(1).id()).isEmpty(), "least recently used run is evicted"),
                () -> assertTrue(store.find(newest.id()).isPresent()));
    }

    // Proves a run is found just before 30 minutes and gone at 30 minutes, even when read in between.
    @Test
    void runExpiresThirtyMinutesAfterCreation() {
        Run run = run(START);
        store.save(run);

        clock.advance(RunStore.TIME_TO_LIVE.minusSeconds(1));
        assertTrue(store.find(run.id()).isPresent());

        clock.advance(Duration.ofSeconds(1));
        assertTrue(store.find(run.id()).isEmpty());
    }

    // Proves the sweep removes only expired runs.
    @Test
    void sweepRemovesOnlyExpiredRuns() {
        Run old = run(START);
        store.save(old);
        clock.advance(Duration.ofMinutes(20));
        Run recent = run(clock.instant());
        store.save(recent);

        clock.advance(Duration.ofMinutes(10));
        store.removeExpired();

        assertAll(
                () -> assertEquals(1, store.size()),
                () -> assertTrue(store.find(recent.id()).isPresent()));
    }

    private static Run run(Instant createdAt) {
        return new Run(UUID.randomUUID(), createdAt, RunConfig.defaults(), List.of(), List.of(), null, 0);
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
