package com.txnscreen.ingest;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.txnscreen.api.ApiException;

/**
 * Parses an uploaded blacklist CSV with a single account_id column into a set of account IDs.
 */
@Component
public class BlacklistParser {

    static final List<String> HEADER = List.of("account_id");

    /**
     * Reads the blacklist, trimming each ID and skipping blank lines and blank IDs. An empty file or a file
     * with only the header gives an empty set.
     *
     * @param input the UTF-8 CSV, which may start with a byte order mark
     * @return the distinct account IDs
     * @throws ApiException 400 BAD_HEADER if the header is not account_id, or 400 MALFORMED_CSV if quoting
     *         is broken
     */
    public Set<String> parse(InputStream input) {
        Set<String> accountIds = new HashSet<>();
        try (CsvRowReader reader = new CsvRowReader(input)) {
            CsvRow header = reader.next();
            if (header == null) {
                return Set.of();
            }
            HeaderCheck.require(header, HEADER);

            for (CsvRow row = reader.next(); row != null; row = reader.next()) {
                String accountId = row.fields().get(0).trim();
                if (!accountId.isEmpty()) {
                    accountIds.add(accountId);
                }
            }
        }
        return Set.copyOf(accountIds);
    }
}
