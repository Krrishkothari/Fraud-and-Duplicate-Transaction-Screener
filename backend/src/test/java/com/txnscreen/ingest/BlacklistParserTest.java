package com.txnscreen.ingest;

import static com.txnscreen.ingest.IngestAssertions.assertApiError;
import static com.txnscreen.ingest.IngestAssertions.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.txnscreen.api.ApiException;

class BlacklistParserTest {

    private final BlacklistParser parser = new BlacklistParser();

    // Proves IDs are trimmed and de-duplicated, and blank lines and blank IDs are skipped.
    @Test
    void idsAreTrimmedAndBlankLinesSkipped() {
        Set<String> ids = parser.parse(utf8("account_id\n ACC1 \n\nACC2\n   \n\"\"\nACC1\n"));

        assertEquals(Set.of("ACC1", "ACC2"), ids);
    }

    // Proves the header match ignores case, surrounding spaces and a byte order mark.
    @Test
    void headerIgnoresCaseSpacesAndByteOrderMark() {
        assertEquals(Set.of("ACC1"), parser.parse(utf8("﻿ Account_ID \r\nACC1\r\n")));
    }

    // Proves an empty blacklist is allowed.
    @ParameterizedTest
    @ValueSource(strings = {"", "account_id", "account_id\n\n"})
    void emptyBlacklistIsAllowed(String content) {
        assertEquals(Set.of(), parser.parse(utf8(content)));
    }

    // Proves a wrong header is refused with the expected header in the details.
    @Test
    void wrongHeaderIsRefused() {
        ApiException error = assertApiError("BAD_HEADER", HttpStatus.BAD_REQUEST,
                () -> parser.parse(utf8("account\nACC1\n")));

        assertEquals("account_id", error.getDetails().get("expected"));
    }

    // Proves the committed sample blacklist parses to one ID per data line.
    @Test
    void sampleBlacklistParses() throws IOException {
        Path sample = Path.of("..", "data", "samples", "blacklist_1000.csv");
        long dataLines = Files.readAllLines(sample).stream().skip(1).filter(line -> !line.isBlank()).count();

        try (InputStream input = Files.newInputStream(sample)) {
            assertEquals(dataLines, parser.parse(input).size());
        }
    }
}
