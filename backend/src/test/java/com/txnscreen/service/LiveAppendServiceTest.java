package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.txnscreen.algo.segtree.WindowResult;
import com.txnscreen.domain.Transaction;

@SpringBootTest
class LiveAppendServiceTest {

    private static final int THREADS = 8;
    private static final int APPENDS_PER_THREAD = 25;

    @Autowired
    private RunService runService;

    @Autowired
    private LiveAppendService liveAppendService;

    @Autowired
    private AnalyticsService analyticsService;

    // Proves concurrent appends and window queries never lose an update or reuse a line number.
    @Test
    void concurrentAppendsAreAllCountedExactlyOnce() throws Exception {
        Run run = runService.createSampleRun(1_000, 42, 0.05, 0.05, RunConfig.defaults());
        int before = run.transactions().size();
        long start = run.analyticsIndex().dataStart();
        long end = run.analyticsIndex().dataEnd();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS + 1);
        try {
            List<Future<Integer>> appenders = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                appenders.add(pool.submit(appendMany(run, thread, start)));
            }
            Future<Boolean> reader = pool.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    WindowResult window = analyticsService.window(run, start, end).value();
                    assertTrue(window.stats().count() >= before);
                }
                return true;
            });
            for (Future<Integer> appender : appenders) {
                assertEquals(APPENDS_PER_THREAD, appender.get());
            }
            assertTrue(reader.get());
        } finally {
            pool.shutdownNow();
        }

        int appended = THREADS * APPENDS_PER_THREAD;
        Set<Integer> lines = new HashSet<>();
        run.transactions().forEach(transaction -> lines.add(transaction.lineNumber()));
        assertEquals(before + appended, run.transactions().size());
        assertEquals(before + appended, analyticsService.window(run, start, end).value().stats().count());
        assertEquals(before + appended, lines.size());
    }

    private Callable<Integer> appendMany(Run run, int thread, long start) {
        return () -> {
            int accepted = 0;
            for (int i = 0; i < APPENDS_PER_THREAD; i++) {
                String timestamp = timestampAt(start + 3_600L * (i + 1));
                AppendResult result = liveAppendService.append(run,
                        List.of("LIVE-" + thread + "-" + i, "ACC_LIVE", "10.00", timestamp, "Live Shop"));
                if (result.transaction().amountPaise() == 1000) {
                    accepted++;
                }
            }
            return accepted;
        };
    }

    // Proves transactions appended out of time order are kept sorted, which the window threshold relies on.
    @Test
    void appendedTransactionsStaySortedByTime() {
        Run run = runService.createSampleRun(200, 7, 0.0, 0.0, RunConfig.defaults());
        long start = run.analyticsIndex().dataStart();

        for (long offset : new long[] {7_200, 60, 3_600, 60}) {
            liveAppendService.append(run, List.of("SORT-" + offset + "-" + System.nanoTime(), "ACC_LIVE", "10.00",
                    timestampAt(start + offset), "Live Shop"));
        }

        List<Transaction> transactions = run.transactions();
        for (int i = 1; i < transactions.size(); i++) {
            assertTrue(transactions.get(i - 1).epochSeconds() <= transactions.get(i).epochSeconds());
        }
    }

    // Proves appends that overfill the duplicate filter refresh both its stats and the run's warnings.
    @Test
    void appendsThatSaturateTheFilterRefreshTheWarnings() {
        Run run = runService.createSampleRun(1_000, 42, 0.05, 0.05,
                new RunConfig(1_000, null, null, null, null, null));
        long start = run.analyticsIndex().dataStart();
        assertTrue(run.screening().warnings().stream().noneMatch(w -> w.contains("duplicate filter")));

        for (int i = 0; i < 200; i++) {
            liveAppendService.append(run,
                    List.of("SAT-" + i, "ACC_LIVE", "10.00", timestampAt(start + i), "Live Shop"));
        }

        assertTrue(run.screening().duplicateStats().saturated());
        assertTrue(run.screening().warnings().stream().anyMatch(w -> w.contains("duplicate filter is saturated")));
    }

    private static String timestampAt(long epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC).toString();
    }
}
