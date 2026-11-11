package com.txnscreen.ingest;

import java.util.List;

import org.apache.commons.csv.CSVFormat;

/**
 * One non-blank CSV record and the 1-based file line it starts on.
 */
record CsvRow(int lineNumber, List<String> fields) {

    CsvRow {
        fields = List.copyOf(fields);
    }

    /**
     * Returns the record as CSV text, quoting any field that needs it.
     */
    String raw() {
        return CSVFormat.DEFAULT.format(fields.toArray());
    }
}
