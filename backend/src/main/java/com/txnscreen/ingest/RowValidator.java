package com.txnscreen.ingest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.txnscreen.domain.Money;
import com.txnscreen.domain.RejectReason;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * Validates one transaction CSV record. Rules are checked in this order: every field present and non-blank,
 * amount parses, amount is positive, timestamp parses as ISO-8601 with an offset. Fields are trimmed, and
 * columns after the fifth are ignored.
 */
public final class RowValidator {

    static final int FIELD_COUNT = 5;

    private RowValidator() {
    }

    /**
     * Validates the fields of one record in the order transaction_id, account_id, amount, timestamp, merchant.
     *
     * @param lineNumber the 1-based file line the record starts on
     * @param fields the record's fields
     * @param raw the record as CSV text, kept on a rejected row
     * @return a {@link RowValidation.Valid} transaction or a {@link RowValidation.Rejected} row
     */
    public static RowValidation validate(int lineNumber, List<String> fields, String raw) {
        if (fields.size() < FIELD_COUNT) {
            return reject(lineNumber, RejectReason.MISSING_FIELD, raw);
        }
        List<String> values = fields.subList(0, FIELD_COUNT).stream().map(String::trim).toList();
        if (values.stream().anyMatch(String::isEmpty)) {
            return reject(lineNumber, RejectReason.MISSING_FIELD, raw);
        }

        long amountPaise;
        try {
            amountPaise = Money.parseToPaise(values.get(2));
        } catch (IllegalArgumentException e) {
            return reject(lineNumber, RejectReason.INVALID_AMOUNT, raw);
        }
        if (amountPaise <= 0) {
            return reject(lineNumber, RejectReason.AMOUNT_NOT_POSITIVE, raw);
        }

        long epochSeconds;
        try {
            epochSeconds = OffsetDateTime.parse(values.get(3)).toEpochSecond();
        } catch (DateTimeParseException e) {
            return reject(lineNumber, RejectReason.INVALID_TIMESTAMP, raw);
        }

        return new RowValidation.Valid(new Transaction(
                lineNumber, values.get(0), values.get(1), amountPaise, epochSeconds, values.get(4)));
    }

    private static RowValidation reject(int lineNumber, RejectReason reason, String raw) {
        return new RowValidation.Rejected(new RejectedRow(lineNumber, reason, raw));
    }
}
