package com.txnscreen.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the most recent runs in memory. It holds at most {@link #MAX_RUNS} runs and evicts the least
 * recently used one when full. A run expires {@link #TIME_TO_LIVE} after creation, and expired runs are
 * removed on every access and by a sweep every {@link #SWEEP_INTERVAL_MS} milliseconds. Thread safe.
 */
@Component
public class RunStore {

    /** The most runs kept at once. */
    public static final int MAX_RUNS = 10;

    /** How long a run is kept after it is created. */
    public static final Duration TIME_TO_LIVE = Duration.ofMinutes(30);

    /** How often expired runs are swept, in milliseconds. */
    public static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000;

    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final Clock clock;
    private final Map<UUID, Run> runs = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Run> eldest) {
            return size() > MAX_RUNS;
        }
    };

    public RunStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * Stores a run as the most recently used, evicting the least recently used run if the store is full.
     *
     * @param run the run to store
     */
    public synchronized void save(Run run) {
        removeExpired();
        runs.put(run.id(), run);
    }

    /**
     * Returns a run that exists and has not expired, and marks it as the most recently used.
     *
     * @param id the run ID
     * @return the run, or empty if it is unknown, evicted or expired
     */
    public synchronized Optional<Run> find(UUID id) {
        removeExpired();
        return Optional.ofNullable(runs.get(id));
    }

    /**
     * Returns the number of stored runs that have not expired.
     *
     * @return the run count
     */
    public synchronized int size() {
        removeExpired();
        return runs.size();
    }

    /**
     * Removes every expired run. Spring calls this on a schedule.
     */
    @Scheduled(fixedRate = SWEEP_INTERVAL_MS, initialDelay = SWEEP_INTERVAL_MS)
    public synchronized void removeExpired() {
        Instant cutoff = clock.instant().minus(TIME_TO_LIVE);
        runs.values().removeIf(run -> !run.createdAt().isAfter(cutoff));
    }
}
