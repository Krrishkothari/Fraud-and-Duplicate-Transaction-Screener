package com.txnscreen.ingest;

import static com.txnscreen.ingest.IngestAssertions.assertApiError;
import static com.txnscreen.ingest.IngestAssertions.utf8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.txnscreen.api.ApiException;
import com.txnscreen.domain.RejectReason;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

class CsvTransactionParserTest {

    private static final String HEADER = "transaction_id,account_id,amount,timestamp,merchant\n";
    private static final Path EDGE_CASES = Path.of("..", "data", "samples", "edge_cases.csv");

    private final CsvTransactionParser parser = new CsvTransactionParser();

    // Proves the committed edge case sample splits into the expected valid and rejected rows.
    @Test
    void edgeCaseSampleSplitsIntoValidAndRejected() throws IOException {
        ParseResult result;
        try (InputStream input = Files.newInputStream(EDGE_CASES)) {
            result = parser.parse(input);
        }

        assertAll(
                () -> assertEquals(17, result.totalRows()),
                () -> assertEquals(12, result.valid().size()),
                () -> assertEquals(List.of(
                        new RejectedRow(5, RejectReason.AMOUNT_NOT_POSITIVE,
                                "EDG0004,ACC000004,-250.00,2026-01-05T10:30:00+05:30,Pharmacy 004"),
                        new RejectedRow(6, RejectReason.AMOUNT_NOT_POSITIVE,
                                "EDG0005,ACC000005,0.00,2026-01-05T11:02:19+05:30,Clothing Store 006"),
                        new RejectedRow(8, RejectReason.INVALID_TIMESTAMP,
                                "EDG0007,ACC000007,320.10,2026-01-05 12:15:00,Grocery Store 009"),
                        new RejectedRow(10, RejectReason.MISSING_FIELD,
                                "EDG0008,,760.00,2026-01-05T13:20:33+05:30,Restaurant 011"),
                        new RejectedRow(11, RejectReason.INVALID_AMOUNT,
                                "EDG0009,ACC000009,120.505,2026-01-05T14:03:12+05:30,Pharmacy 012")),
                        result.rejected()));
    }

    // Proves duplicate transaction IDs are both kept, since screening is what flags them.
    @Test
    void duplicateTransactionIdsAreKept() {
        ParseResult result = parser.parse(utf8(HEADER
                + "TXN1,ACC1,10.00,2026-01-05T09:00:00Z,Shop\n"
                + "TXN1,ACC1,10.00,2026-01-05T09:00:00Z,Shop\n"));

        assertEquals(List.of("TXN1", "TXN1"), transactionIds(result));
    }

    // Proves valid rows are sorted by instant, with equal instants in file order, across offsets.
    @Test
    void validRowsAreSortedStablyByTime() {
        ParseResult result = parser.parse(utf8(HEADER
                + "LATE,ACC1,1,2026-01-05T12:00:00Z,Shop\n"
                + "TIE_A,ACC1,1,2026-01-05T15:30:00+05:30,Shop\n"
                + "EARLY,ACC1,1,2026-01-05T09:00:00Z,Shop\n"
                + "TIE_B,ACC1,1,2026-01-05T10:00:00Z,Shop\n"
                + "TIE_C,ACC1,1,2026-01-05T05:00:00-05:00,Shop\n"));

        assertEquals(List.of("EARLY", "TIE_A", "TIE_B", "TIE_C", "LATE"), transactionIds(result));
    }

    // Proves line numbers count the header, blank lines, CRLF endings and multi-line quoted fields.
    @Test
    void lineNumbersMatchTheFile() {
        ParseResult result = parser.parse(utf8("transaction_id,account_id,amount,timestamp,merchant\r\n"
                + "TXN1,ACC1,1,2026-01-05T09:00:00Z,Shop\r\n"
                + "\r\n"
                + "TXN2,ACC1,1,2026-01-05T09:00:00Z,\"Two\r\nLines\"\r\n"
                + "TXN3,ACC1,0,2026-01-05T09:00:00Z,Shop"));

        assertAll(
                () -> assertEquals(3, result.totalRows()),
                () -> assertEquals(List.of(2, 4), result.valid().stream().map(Transaction::lineNumber).toList()),
                () -> assertEquals("Two\r\nLines", result.valid().get(1).merchant()),
                () -> assertEquals(6, result.rejected().get(0).lineNumber()));
    }

    // Proves a quoted merchant may contain the delimiter.
    @Test
    void quotedMerchantMayContainComma() {
        ParseResult result = parser.parse(utf8(HEADER + "TXN1,ACC1,1,2026-01-05T09:00:00Z,\"Shop, Inc\"\n"));

        assertEquals("Shop, Inc", result.valid().get(0).merchant());
    }

    // Proves the header match ignores case, surrounding spaces and a byte order mark.
    @Test
    void headerIgnoresCaseSpacesAndByteOrderMark() {
        ParseResult result = parser.parse(utf8("﻿ Transaction_ID , ACCOUNT_ID,Amount,timestamp , Merchant\n"
                + "TXN1,ACC1,1,2026-01-05T09:00:00Z,Shop\n"));

        assertEquals(List.of("TXN1"), transactionIds(result));
    }

    // Proves a wrong header is refused with the expected header in the details.
    @ParameterizedTest
    @ValueSource(strings = {
        "transaction_id,account_id,amount,timestamp\n",
        "account_id,transaction_id,amount,timestamp,merchant\n",
        "TXN1,ACC1,1,2026-01-05T09:00:00Z,Shop\n"})
    void wrongHeaderIsRefused(String header) {
        ApiException error = assertApiError("BAD_HEADER", HttpStatus.BAD_REQUEST,
                () -> parser.parse(utf8(header + "TXN1,ACC1,1,2026-01-05T09:00:00Z,Shop\n")));

        assertEquals("transaction_id,account_id,amount,timestamp,merchant", error.getDetails().get("expected"));
    }

    // Proves a file with no data rows is refused, whether it is empty, header only or header and blank lines.
    @ParameterizedTest
    @ValueSource(strings = {"", "\n\n", HEADER, HEADER + "\n  \n"})
    void fileWithoutDataRowsIsEmpty(String content) {
        assertApiError("EMPTY_FILE", HttpStatus.BAD_REQUEST, () -> parser.parse(utf8(content)));
    }

    // Proves an unclosed quote is refused with the line it starts on.
    @Test
    void unclosedQuoteIsMalformed() {
        ApiException error = assertApiError("MALFORMED_CSV", HttpStatus.BAD_REQUEST, () -> parser.parse(utf8(HEADER
                + "TXN1,ACC1,1,2026-01-05T09:00:00Z,Shop\n"
                + "TXN2,ACC1,1,2026-01-05T09:00:00Z,\"Shop\n")));

        assertEquals(Map.of("line", 3), error.getDetails());
    }

    // Proves exactly the row limit is accepted.
    @Test
    void rowLimitIsAccepted() {
        ParseResult result = parser.parse(rows(CsvTransactionParser.MAX_DATA_ROWS));

        assertEquals(CsvTransactionParser.MAX_DATA_ROWS, result.totalRows());
    }

    // Proves one row over the limit is refused as too large.
    @Test
    void oneRowOverLimitIsTooLarge() {
        ApiException error = assertApiError("FILE_TOO_LARGE", HttpStatus.CONTENT_TOO_LARGE,
                () -> parser.parse(rows(CsvTransactionParser.MAX_DATA_ROWS + 1)));

        assertEquals(CsvTransactionParser.MAX_DATA_ROWS, error.getDetails().get("maxRows"));
    }

    private static InputStream rows(int count) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < count; i++) {
            csv.append("TXN").append(i).append(",ACC1,1,2026-01-05T09:00:00Z,Shop\n");
        }
        return utf8(csv.toString());
    }

    private static List<String> transactionIds(ParseResult result) {
        return result.valid().stream().map(Transaction::transactionId).toList();
    }
}
