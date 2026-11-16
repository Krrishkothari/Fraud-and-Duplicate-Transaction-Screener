package com.txnscreen.api;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.txnscreen.service.RunStore;

/**
 * Uploads files that are empty, have the wrong header or hold invalid rows, and checks the error or rejected
 * row the API returns for each.
 */
@SpringBootTest
class IngestionEdgeCaseTest {

    private static final String HEADER = "transaction_id,account_id,amount,timestamp,merchant";
    private static final String VALID_ROW = "T1,ACC1,100.00,2026-01-05T09:00:00+05:30,Shop";
    private static final String NEGATIVE_AMOUNT_ROW = "T2,ACC1,-250.00,2026-01-05T09:05:00+05:30,Shop";
    private static final String ZERO_AMOUNT_ROW = "T3,ACC1,0.00,2026-01-05T09:10:00+05:30,Shop";
    private static final String BAD_TIMESTAMP_ROW = "T4,ACC1,10.00,2026-01-05 09:15:00,Shop";
    private static final String BLANK_FIELD_ROW = "T5,,10.00,2026-01-05T09:20:00+05:30,Shop";
    private static final String SHORT_ROW = "T6,ACC1,10.00,2026-01-05T09:25:00+05:30";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RunStore runStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves a zero byte transactions file is 400 EMPTY_FILE.
    @Test
    void emptyFileIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/runs").file(csv("transactions", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.message").value("The file has no data rows."))
                .andExpect(jsonPath("$.details", anEmptyMap()));
    }

    // Proves a file with the header and no data rows is 400 EMPTY_FILE.
    @Test
    void headerOnlyFileIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/runs").file(csv("transactions", HEADER + "\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    // Proves a transactions header with columns out of order is 400 BAD_HEADER, naming the expected header.
    @Test
    void wrongTransactionsHeaderIsRefused() throws Exception {
        String wrongHeader = "transaction_id,account_id,timestamp,amount,merchant";

        mockMvc.perform(multipart("/api/runs").file(csv("transactions", wrongHeader + "\n" + VALID_ROW + "\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_HEADER"))
                .andExpect(jsonPath("$.message").value("The header must be: " + HEADER + "."))
                .andExpect(jsonPath("$.details.expected").value(HEADER))
                .andExpect(jsonPath("$.details.actual").value(wrongHeader));
    }

    // Proves a blacklist with the wrong header is 400 BAD_HEADER, naming its expected header.
    @Test
    void wrongBlacklistHeaderIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", HEADER + "\n" + VALID_ROW + "\n"))
                        .file(csv("blacklist", "account\nACC1\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_HEADER"))
                .andExpect(jsonPath("$.details.expected").value("account_id"))
                .andExpect(jsonPath("$.details.actual").value("account"));
    }

    // Proves a negative amount row is rejected as AMOUNT_NOT_POSITIVE while the run still succeeds.
    @Test
    void negativeAmountRowIsRejected() throws Exception {
        expectRejectedAtLineThree(NEGATIVE_AMOUNT_ROW, "AMOUNT_NOT_POSITIVE");
    }

    // Proves a zero amount row is rejected as AMOUNT_NOT_POSITIVE.
    @Test
    void zeroAmountRowIsRejected() throws Exception {
        expectRejectedAtLineThree(ZERO_AMOUNT_ROW, "AMOUNT_NOT_POSITIVE");
    }

    // Proves a timestamp without the ISO-8601 T separator and offset is rejected as INVALID_TIMESTAMP.
    @Test
    void badTimestampRowIsRejected() throws Exception {
        expectRejectedAtLineThree(BAD_TIMESTAMP_ROW, "INVALID_TIMESTAMP");
    }

    // Proves a blank field and a row with too few columns are both rejected as MISSING_FIELD.
    @Test
    void missingFieldRowIsRejected() throws Exception {
        expectRejectedAtLineThree(BLANK_FIELD_ROW, "MISSING_FIELD");
        expectRejectedAtLineThree(SHORT_ROW, "MISSING_FIELD");
    }

    // Proves a file where every row is invalid is 422 NO_VALID_ROWS, lists each row's reason and stores no run.
    @Test
    void fileWithOnlyInvalidRowsIsUnprocessable() throws Exception {
        String transactions = String.join("\n", HEADER, NEGATIVE_AMOUNT_ROW, ZERO_AMOUNT_ROW, BAD_TIMESTAMP_ROW,
                BLANK_FIELD_ROW, SHORT_ROW) + "\n";
        int storedRuns = runStore.size();

        mockMvc.perform(multipart("/api/runs").file(csv("transactions", transactions)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_VALID_ROWS"))
                .andExpect(jsonPath("$.message").value("Every row in the file is invalid."))
                .andExpect(jsonPath("$.details.totalRows").value(5))
                .andExpect(jsonPath("$.details.rejectedRows[*].lineNumber", contains(2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.details.rejectedRows[*].reason", contains("AMOUNT_NOT_POSITIVE",
                        "AMOUNT_NOT_POSITIVE", "INVALID_TIMESTAMP", "MISSING_FIELD", "MISSING_FIELD")));
        assertEquals(storedRuns, runStore.size());
    }

    private void expectRejectedAtLineThree(String invalidRow, String reason) throws Exception {
        String transactions = HEADER + "\n" + VALID_ROW + "\n" + invalidRow + "\n";
        MvcResult created = mockMvc.perform(multipart("/api/runs").file(csv("transactions", transactions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/runs/" + id + "/rejected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].lineNumber").value(3))
                .andExpect(jsonPath("$.items[0].reason").value(reason))
                .andExpect(jsonPath("$.items[0].raw").value(invalidRow));
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
