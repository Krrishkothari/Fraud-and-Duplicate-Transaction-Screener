package com.txnscreen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.api.ApiException;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.Money;
import com.txnscreen.domain.RejectReason;
import com.txnscreen.domain.Transaction;
import com.txnscreen.ingest.RowValidation;
import com.txnscreen.ingest.RowValidator;

/**
 * Adds one live transaction to a stored run: validates it, screens it, checks it against the run's threshold and
 * applies a segment tree point update. All of it happens while holding the run's lock, so appends and window
 * queries never interleave.
 */
@Service
public class LiveAppendService {

    /** The names of the transaction fields, in the order {@link RowValidator} expects them. */
    public static final List<String> FIELD_NAMES =
            List.of("transactionId", "accountId", "amount", "timestamp", "merchant");

    private final ThresholdService thresholdService;

    public LiveAppendService(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    /**
     * Appends one transaction to a run.
     *
     * <p>The time index is updated first, because it is the only step that can still reject a valid row. A
     * rejected append therefore leaves the run exactly as it was, and the same transaction ID can be sent
     * again without being flagged as its own duplicate.
     *
     * <p>The threshold is not recomputed: the transaction is compared with the threshold set when the run was
     * created.
     *
     * <p>Time complexity: O(k) screening plus O(log B) for the point update, where B is the bucket count,
     * plus O(n) to insert into the sorted transaction list and O(m / 64) to refresh the filter stats, for n
     * transactions and m filter bits. When the update has to grow the index it is O(B), which is O(1)
     * amortized over appends. Space complexity: O(n).
     *
     * @param run the run to append to
     * @param fields the transaction ID, account ID, amount, timestamp and merchant, in that order, with
     *        missing fields as empty text
     * @return the accepted transaction with its flags, bucket and timings
     * @throws ApiException 400 with the {@link RejectReason} as the code if the fields are invalid, or 422
     *         TIMESTAMP_BEFORE_RANGE or TOO_MANY_BUCKETS if the time index cannot take the timestamp
     */
    public AppendResult append(Run run, List<String> fields) {
        synchronized (run.lock()) {
            Transaction transaction = validate(run, fields);
            TimeWindowIndex index = run.analyticsIndex();

            long updateStart = System.nanoTime();
            index.add(transaction);
            long updateMicros = micros(updateStart);

            long screenStart = System.nanoTime();
            List<Flag> flags = new ArrayList<>(run.screener().screenOne(transaction));
            thresholdService.highAmountFlag(transaction, run.threshold()).ifPresent(flags::add);
            long screenMicros = micros(screenStart);

            run.insertTransaction(transaction);
            publish(run, flags);

            long epochSeconds = transaction.epochSeconds();
            return new AppendResult(transaction, List.copyOf(flags), index.bucketIndex(epochSeconds),
                    index.query(epochSeconds, epochSeconds).stats(), screenMicros, updateMicros,
                    thresholdNote(run.threshold()));
        }
    }

    private static Transaction validate(Run run, List<String> fields) {
        String raw = CSVFormat.DEFAULT.format(fields.toArray());
        return switch (RowValidator.validate(run.nextLineNumber(), fields, raw)) {
            case RowValidation.Valid valid -> valid.transaction();
            case RowValidation.Rejected rejected -> throw rejection(rejected.row().reason(), fields);
        };
    }

    private static ApiException rejection(RejectReason reason, List<String> fields) {
        return switch (reason) {
            case MISSING_FIELD -> invalid(reason, "A required field is missing or blank.",
                    Map.of("fields", blankFieldNames(fields)));
            case INVALID_AMOUNT -> invalid(reason, "Amount must be a number with at most 2 decimal places.",
                    Map.of("field", "amount"));
            case AMOUNT_NOT_POSITIVE -> invalid(reason, "Amount must be greater than zero.",
                    Map.of("field", "amount"));
            case INVALID_TIMESTAMP -> invalid(reason,
                    "Timestamp must be ISO-8601 with an offset, such as 2026-03-14T09:26:53+05:30.",
                    Map.of("field", "timestamp"));
        };
    }

    private static ApiException invalid(RejectReason reason, String message, Map<String, Object> details) {
        return new ApiException(reason.name(), HttpStatus.BAD_REQUEST, message, details);
    }

    private static List<String> blankFieldNames(List<String> fields) {
        List<String> blank = new ArrayList<>();
        for (int i = 0; i < FIELD_NAMES.size(); i++) {
            if (fields.get(i).isBlank()) {
                blank.add(FIELD_NAMES.get(i));
            }
        }
        return blank;
    }

    /** Adds the new flags to the run's flags and refreshes the filter stats and warnings, as one replacement. */
    private static void publish(Run run, List<Flag> newFlags) {
        List<Flag> flags = new ArrayList<>(run.screening().flags());
        flags.addAll(newFlags);
        run.setScreening(ScreeningService.snapshot(run.screener(), flags));
    }

    private static String thresholdNote(ThresholdResult threshold) {
        return "The P" + ThresholdService.percentileText(threshold) + " threshold of "
                + Money.format(threshold.valuePaise())
                + " was set when the run was created and is not recomputed when a transaction is appended.";
    }

    private static long micros(long startNanos) {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
    }
}
