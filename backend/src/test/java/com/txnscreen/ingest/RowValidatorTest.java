package com.txnscreen.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.txnscreen.domain.RejectReason;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

class RowValidatorTest {

    private static final int LINE = 7;
    private static final String RAW = "raw text";

    // Proves a good row becomes a transaction with trimmed fields, paise and epoch seconds.
    @Test
    void validRowBecomesTransaction() {
        RowValidation result = RowValidator.validate(LINE,
                List.of(" TXN1 ", "ACC1", " 450.5 ", "2026-01-05T09:12:30+05:30", " Grocery Store 001 "), RAW);

        Transaction transaction = assertInstanceOf(RowValidation.Valid.class, result).transaction();
        assertEquals(new Transaction(LINE, "TXN1", "ACC1", 45_050, 1_767_584_550L, "Grocery Store 001"),
                transaction);
    }

    // Proves columns after the fifth are ignored.
    @Test
    void extraColumnsAreIgnored() {
        RowValidation result = RowValidator.validate(LINE,
                List.of("TXN1", "ACC1", "10", "2026-01-05T09:12:30Z", "Shop", "extra"), RAW);

        assertInstanceOf(RowValidation.Valid.class, result);
    }

    // Proves a short row is rejected as missing a field, keeping its line and raw text.
    @Test
    void shortRowIsMissingField() {
        RowValidation result = RowValidator.validate(LINE, List.of("TXN1", "ACC1", "10", "2026-01-05T09:12:30Z"), RAW);

        assertEquals(new RejectedRow(LINE, RejectReason.MISSING_FIELD, RAW),
                assertInstanceOf(RowValidation.Rejected.class, result).row());
    }

    // Proves a field that is only spaces counts as missing, and is checked before the amount.
    @Test
    void blankFieldIsMissingFieldBeforeAmountCheck() {
        assertReason(RejectReason.MISSING_FIELD, List.of("TXN1", "   ", "abc", "2026-01-05T09:12:30Z", "Shop"));
    }

    // Proves amounts that Money cannot parse are rejected as invalid.
    @ParameterizedTest
    @ValueSource(strings = {"120.505", "abc", "1,000.00", "+5", "99999999999999999999"})
    void unparseableAmountIsInvalid(String amount) {
        assertReason(RejectReason.INVALID_AMOUNT, row(amount, "2026-01-05T09:12:30Z"));
    }

    // Proves zero and negative amounts are rejected as not positive.
    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-250.00"})
    void zeroOrNegativeAmountIsNotPositive(String amount) {
        assertReason(RejectReason.AMOUNT_NOT_POSITIVE, row(amount, "2026-01-05T09:12:30Z"));
    }

    // Proves timestamps without an offset or in another format are rejected.
    @ParameterizedTest
    @ValueSource(strings = {"2026-01-05 12:15:00", "2026-01-05T12:15:00", "2026-01-05", "2026-13-05T12:15:00Z"})
    void timestampWithoutIsoOffsetIsInvalid(String timestamp) {
        assertReason(RejectReason.INVALID_TIMESTAMP, row("10.00", timestamp));
    }

    private static List<String> row(String amount, String timestamp) {
        return List.of("TXN1", "ACC1", amount, timestamp, "Shop");
    }

    private static void assertReason(RejectReason expected, List<String> fields) {
        RowValidation result = RowValidator.validate(LINE, fields, RAW);
        assertEquals(expected, assertInstanceOf(RowValidation.Rejected.class, result).row().reason());
    }
}
