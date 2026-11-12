package com.txnscreen.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.txnscreen.api.ApiException;
import com.txnscreen.data.GeneratedData;
import com.txnscreen.data.SyntheticDataGenerator;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.ingest.ParseResult;

/**
 * Creates runs by screening parsed or generated transactions, and looks them up again.
 */
@Service
public class RunService {

    /** The most rejected rows returned when a file has no valid rows. */
    public static final int MAX_REJECTED_IN_ERROR = 50;

    private final ScreeningService screeningService;
    private final AnalyticsService analyticsService;
    private final RunStore runStore;
    private final Clock clock;

    public RunService(ScreeningService screeningService, AnalyticsService analyticsService, RunStore runStore,
            Clock clock) {
        this.screeningService = screeningService;
        this.analyticsService = analyticsService;
        this.runStore = runStore;
        this.clock = clock;
    }

    /**
     * Screens the valid transactions, builds the time window index and stores the run. Screening and the
     * index build are timed separately.
     *
     * @param parsed the parsed upload
     * @param blacklist the blacklisted account IDs, which may be empty
     * @param config the run settings
     * @return the stored run
     * @throws ApiException 422 NO_VALID_ROWS with the first {@link #MAX_REJECTED_IN_ERROR} rejected rows if
     *         every row is invalid, 400 INVALID_CONFIG if a filter would be too large, or 422
     *         TOO_MANY_BUCKETS if the time range needs more buckets than the index allows
     */
    public Run createRun(ParseResult parsed, Set<String> blacklist, RunConfig config) {
        if (parsed.valid().isEmpty()) {
            List<RejectedRow> firstRejected =
                    parsed.rejected().subList(0, Math.min(MAX_REJECTED_IN_ERROR, parsed.rejected().size()));
            throw new ApiException("NO_VALID_ROWS", HttpStatus.UNPROCESSABLE_CONTENT,
                    "Every row in the file is invalid.",
                    Map.of("totalRows", parsed.totalRows(), "rejectedRows", List.copyOf(firstRejected)));
        }

        long start = System.nanoTime();
        ScreeningResult screening = screeningService.screenAll(parsed.valid(), blacklist, config);
        long screeningMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);

        Run run = new Run(UUID.randomUUID(), clock.instant(), config, parsed.valid(), parsed.rejected(), screening,
                screeningMicros);
        analyticsService.attachIndex(run);
        runStore.save(run);
        return run;
    }

    /**
     * Generates a synthetic dataset and screens it as a run. Generation is not part of the screening time.
     *
     * @param size the number of rows
     * @param seed the generator seed
     * @param duplicateRate the share of rows that repeat an earlier transaction ID
     * @param blacklistRate the share of accounts that are blacklisted
     * @param config the run settings
     * @return the stored run
     */
    public Run createSampleRun(int size, long seed, double duplicateRate, double blacklistRate, RunConfig config) {
        GeneratedData data = SyntheticDataGenerator.generate(size, seed, duplicateRate, blacklistRate);
        ParseResult parsed = new ParseResult(data.rows(), List.of(), data.rows().size());
        return createRun(parsed, data.blacklist(), config);
    }

    /**
     * Returns a stored run that has not expired.
     *
     * @param id the run ID as text
     * @return the run
     * @throws ApiException 404 RUN_NOT_FOUND if the ID is malformed, unknown, evicted or expired
     */
    public Run getRun(String id) {
        UUID runId;
        try {
            runId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw runNotFound(id);
        }
        return runStore.find(runId).orElseThrow(() -> runNotFound(id));
    }

    private static ApiException runNotFound(String id) {
        return new ApiException("RUN_NOT_FOUND", HttpStatus.NOT_FOUND,
                "No run with this ID exists. Runs expire " + RunStore.TIME_TO_LIVE.toMinutes()
                        + " minutes after they are created.",
                Map.of("runId", id));
    }
}
