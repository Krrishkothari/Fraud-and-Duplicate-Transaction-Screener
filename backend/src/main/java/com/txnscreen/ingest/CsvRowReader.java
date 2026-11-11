package com.txnscreen.ingest;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;

import com.txnscreen.api.ApiException;

/**
 * Streams UTF-8 CSV records one at a time, stripping a leading byte order mark, skipping blank lines and
 * keeping the 1-based line number each record starts on.
 */
final class CsvRowReader implements Closeable {

    private static final int BYTE_ORDER_MARK = '﻿';
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).get();

    private final CSVParser parser;
    private final Iterator<CSVRecord> records;

    CsvRowReader(InputStream input) {
        try {
            parser = CSVParser.parse(withoutByteOrderMark(input), FORMAT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        records = parser.iterator();
    }

    /**
     * Returns the next non-blank record, or null at the end of the input.
     *
     * @throws ApiException 400 MALFORMED_CSV if the record has broken quoting
     */
    CsvRow next() {
        while (true) {
            int lineNumber = Math.toIntExact(parser.getCurrentLineNumber() + 1);
            CSVRecord record = readRecord(lineNumber);
            if (record == null) {
                return null;
            }
            if (!isBlankLine(record)) {
                return new CsvRow(lineNumber, record.toList());
            }
        }
    }

    @Override
    public void close() {
        try {
            parser.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CSVRecord readRecord(int lineNumber) {
        try {
            return records.hasNext() ? records.next() : null;
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof CSVException) {
                throw new ApiException("MALFORMED_CSV", HttpStatus.BAD_REQUEST,
                        "The CSV is malformed from line " + lineNumber + ", check for an unclosed quote.",
                        Map.of("line", lineNumber));
            }
            throw e;
        }
    }

    private static boolean isBlankLine(CSVRecord record) {
        return record.size() == 1 && record.get(0).isBlank();
    }

    private static Reader withoutByteOrderMark(InputStream input) throws IOException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        int first = reader.read();
        if (first != BYTE_ORDER_MARK && first != -1) {
            reader.unread(first);
        }
        return reader;
    }
}
