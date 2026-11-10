package com.txnscreen.algo.segtree;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.txnscreen.api.ApiException;
import com.txnscreen.domain.Transaction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TimeWindowIndexTest {

    /** 2026-01-01T00:00:00+05:30, a whole minute, so it is the first bucket's start (t0). */
    private static final long BASE = 1_767_205_800L;

    private static final int BUCKET_SECONDS = 60;

    // Buckets of 60 s from BASE: bucket 0 holds 1000 and 3000, bucket 1 holds 500,
    // buckets 2 and 3 are empty, bucket 4 holds 7000.
    private static TimeWindowIndex sampleIndex() {
        return TimeWindowIndex.build(List.of(
                txn(1, BASE + 250, 7000),
                txn(2, BASE + 10, 1000),
                txn(3, BASE + 60, 500),
                txn(4, BASE + 50, 3000)), BUCKET_SECONDS);
    }

    // Proves timestamps map to (ts - t0) / w with t0 rounded down to the bucket width.
    @Test
    void mapsTimestampsToBuckets() {
        TimeWindowIndex index = sampleIndex();

        assertAll(
                () -> assertEquals(BASE + 10, index.dataStart()),
                () -> assertEquals(BASE + 250, index.dataEnd()),
                () -> assertEquals(BUCKET_SECONDS, index.bucketSeconds()),
                () -> assertEquals(5, index.bucketCount()),
                () -> assertEquals(new NodeStats(4000, 2, 3000),
                        index.query(BASE + 10, BASE + 59).stats()),
                () -> assertEquals(new NodeStats(500, 1, 500),
                        index.query(BASE + 60, BASE + 60).stats()),
                () -> assertEquals(NodeStats.EMPTY, index.query(BASE + 120, BASE + 239).stats()),
                () -> assertEquals(new NodeStats(7000, 1, 7000),
                        index.query(BASE + 240, BASE + 250).stats()));
    }

    // Proves a window partly outside the data is narrowed to the data range and flagged as clamped.
    @Test
    void clampsWindowPartlyOutsideData() {
        TimeWindowIndex index = sampleIndex();

        WindowResult before = index.query(BASE - 1_000, BASE + 70);
        WindowResult after = index.query(BASE + 100, BASE + 9_999);
        WindowResult inside = index.query(BASE + 10, BASE + 250);

        assertAll(
                () -> assertTrue(before.clamped()),
                () -> assertEquals(BASE + 10, before.effectiveFrom()),
                () -> assertEquals(BASE + 70, before.effectiveTo()),
                () -> assertEquals(new NodeStats(4500, 3, 3000), before.stats()),
                () -> assertTrue(after.clamped()),
                () -> assertEquals(BASE + 100, after.effectiveFrom()),
                () -> assertEquals(BASE + 250, after.effectiveTo()),
                () -> assertEquals(new NodeStats(7500, 2, 7000), after.stats()),
                () -> assertFalse(inside.clamped()),
                () -> assertEquals(new NodeStats(11500, 4, 7000), inside.stats()));
    }

    // Proves a window whose start is after its end is rejected with 400 INVALID_WINDOW.
    @Test
    void rejectsStartAfterEnd() {
        TimeWindowIndex index = sampleIndex();

        ApiException e = assertThrows(ApiException.class,
                () -> index.query(BASE + 100, BASE + 50));

        assertEquals("INVALID_WINDOW", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    // Proves a window entirely before or after the data is rejected with both ranges in details.
    @Test
    void rejectsWindowFullyOutsideData() {
        TimeWindowIndex index = sampleIndex();

        for (long[] window : new long[][] {{BASE - 100, BASE + 9}, {BASE + 251, BASE + 999}}) {
            ApiException e = assertThrows(ApiException.class,
                    () -> index.query(window[0], window[1]));
            assertAll(
                    () -> assertEquals("WINDOW_OUT_OF_RANGE", e.getCode()),
                    () -> assertEquals(HttpStatus.BAD_REQUEST, e.getStatus()),
                    () -> assertEquals(window[0], e.getDetails().get("requestedFrom")),
                    () -> assertEquals(window[1], e.getDetails().get("requestedTo")),
                    () -> assertEquals(BASE + 10, e.getDetails().get("dataStart")),
                    () -> assertEquals(BASE + 250, e.getDetails().get("dataEnd")));
        }
    }

    // Proves appending past the last bucket grows the index and leaves earlier windows unchanged.
    @Test
    void growsPastLastBucketWithoutChangingEarlierWindows() {
        TimeWindowIndex index = sampleIndex();
        WindowResult firstBucket = index.query(BASE, BASE + 59);
        WindowResult laterBuckets = index.query(BASE + 60, BASE + 250);
        WindowResult everything = index.query(BASE + 10, BASE + 250);

        long farTimestamp = BASE + 1_000L * BUCKET_SECONDS + 5;
        index.add(txn(5, farTimestamp, 9900));

        assertAll(
                () -> assertEquals(1_001, index.bucketCount()),
                () -> assertEquals(farTimestamp, index.dataEnd()),
                () -> assertEquals(firstBucket, index.query(BASE, BASE + 59)),
                () -> assertEquals(laterBuckets, index.query(BASE + 60, BASE + 250)),
                () -> assertEquals(everything, index.query(BASE + 10, BASE + 250)),
                () -> assertEquals(new NodeStats(9900, 1, 9900),
                        index.query(farTimestamp, farTimestamp).stats()),
                () -> assertEquals(new NodeStats(21400, 5, 9900),
                        index.query(BASE + 10, farTimestamp).stats()));
    }

    // Proves an append before the first bucket is rejected, while one inside it extends dataStart.
    @Test
    void rejectsAppendBeforeFirstBucket() {
        TimeWindowIndex index = sampleIndex();

        ApiException e = assertThrows(ApiException.class, () -> index.add(txn(5, BASE - 1, 100)));
        index.add(txn(6, BASE + 5, 200));

        assertAll(
                () -> assertEquals("TIMESTAMP_BEFORE_RANGE", e.getCode()),
                () -> assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus()),
                () -> assertEquals(BASE + 5, index.dataStart()),
                () -> assertEquals(new NodeStats(4200, 3, 3000),
                        index.query(BASE, BASE + 59).stats()));
    }

    // Proves invalid build input is rejected instead of producing an unusable index.
    @Test
    void rejectsInvalidBuildInput() {
        List<Transaction> one = List.of(txn(1, BASE, 100));
        List<Transaction> tooWide = List.of(
                txn(1, BASE, 100), txn(2, BASE + TimeWindowIndex.MAX_BUCKETS, 100));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TimeWindowIndex.build(List.of(), BUCKET_SECONDS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TimeWindowIndex.build(one, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TimeWindowIndex.build(one, 86_401)),
                () -> assertEquals("TOO_MANY_BUCKETS", assertThrows(ApiException.class,
                        () -> TimeWindowIndex.build(tooWide, 1)).getCode()));
    }

    private static Transaction txn(int line, long epochSeconds, long amountPaise) {
        return new Transaction(line, "TXN" + line, "ACC000001", amountPaise, epochSeconds,
                "Grocery Store 001");
    }
}
