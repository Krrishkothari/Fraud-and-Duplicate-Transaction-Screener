package com.txnscreen.ingest;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.txnscreen.api.ApiException;

/**
 * Compares a CSV header record with the expected column names, ignoring case and surrounding spaces.
 */
final class HeaderCheck {

    private HeaderCheck() {
    }

    /**
     * Throws unless the header row has exactly the expected column names in order.
     *
     * @param header the first non-blank record of the file
     * @param expected the expected column names, in lower case
     * @throws ApiException 400 BAD_HEADER with the expected and actual header in the details
     */
    static void require(CsvRow header, List<String> expected) {
        List<String> actual = header.fields().stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!actual.equals(expected)) {
            String expectedHeader = String.join(",", expected);
            throw new ApiException("BAD_HEADER", HttpStatus.BAD_REQUEST,
                    "The header must be: " + expectedHeader + ".",
                    Map.of("expected", expectedHeader, "actual", header.raw()));
        }
    }
}
