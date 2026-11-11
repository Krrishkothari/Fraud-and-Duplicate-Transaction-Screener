package com.txnscreen.ingest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.txnscreen.api.ApiException;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * Parses an uploaded transaction CSV one record at a time into valid transactions and rejected rows.
 * Duplicate transaction IDs are kept, since screening is what flags them.
 */
@Component
public class CsvTransactionParser {

    /** The most data rows one file may hold. */
    public static final int MAX_DATA_ROWS = 200_000;

    static final List<String> HEADER = List.of("transaction_id", "account_id", "amount", "timestamp", "merchant");

    /**
     * Reads the CSV, validates every data row and sorts the valid transactions by time. The sort is stable,
     * so transactions with equal times keep their file order.
     *
     * @param input the UTF-8 CSV, which may start with a byte order mark
     * @return the valid transactions, rejected rows and data row count
     * @throws ApiException 400 EMPTY_FILE if there are no data rows, 400 BAD_HEADER if the header is wrong,
     *         400 MALFORMED_CSV if quoting is broken, or 413 FILE_TOO_LARGE above {@link #MAX_DATA_ROWS} rows
     */
    public ParseResult parse(InputStream input) {
        List<Transaction> valid = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        int totalRows = 0;

        try (CsvRowReader reader = new CsvRowReader(input)) {
            CsvRow header = reader.next();
            if (header == null) {
                throw emptyFile();
            }
            HeaderCheck.require(header, HEADER);

            for (CsvRow row = reader.next(); row != null; row = reader.next()) {
                if (totalRows == MAX_DATA_ROWS) {
                    throw tooManyRows();
                }
                totalRows++;
                switch (RowValidator.validate(row.lineNumber(), row.fields(), row.raw())) {
                    case RowValidation.Valid(Transaction transaction) -> valid.add(transaction);
                    case RowValidation.Rejected(RejectedRow rejectedRow) -> rejected.add(rejectedRow);
                }
            }
        }

        if (totalRows == 0) {
            throw emptyFile();
        }
        valid.sort(Comparator.comparingLong(Transaction::epochSeconds));
        return new ParseResult(valid, rejected, totalRows);
    }

    private static ApiException emptyFile() {
        return new ApiException("EMPTY_FILE", HttpStatus.BAD_REQUEST, "The file has no data rows.", Map.of());
    }

    private static ApiException tooManyRows() {
        return new ApiException("FILE_TOO_LARGE", HttpStatus.CONTENT_TOO_LARGE,
                "The file has more than " + MAX_DATA_ROWS + " data rows.", Map.of("maxRows", MAX_DATA_ROWS));
    }
}
