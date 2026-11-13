package com.txnscreen.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.txnscreen.domain.Money;

@SpringBootTest
class LiveTransactionControllerTest {

    private static final Path SAMPLES = Path.of("..", "data", "samples");

    /** 2026-01-01T09:00:00+05:30, well inside the 1K sample's 30 day range. */
    private static final String IN_RANGE = "2026-01-01T09:00:00+05:30";
    private static final long IN_RANGE_EPOCH = 1_767_238_200L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private String runId;
    private String summary;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        summary = mockMvc.perform(multipart("/api/runs")
                        .file(sample("transactions", "transactions_1000.csv"))
                        .file(sample("blacklist", "blacklist_1000.csv")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        runId = JsonPath.read(summary, "$.id");
    }

    // Proves a repeated ID is flagged DUPLICATE and its time window count rises by one (the H07 done check).
    @Test
    void existingIdIsFlaggedDuplicateAndCountsInItsWindow() throws Exception {
        String existingId = firstLine("transactions_1000.csv", 1).split(",")[0];
        int before = windowCount();

        mockMvc.perform(append(existingId, "ACC_LIVE_1", "10.00", IN_RANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.length()").value(1))
                .andExpect(jsonPath("$.flags[0].type").value("DUPLICATE"))
                .andExpect(jsonPath("$.flags[0].detail").value(containsString("Duplicate of line 2")))
                .andExpect(jsonPath("$.bucketStats.count").value(before + 1));

        assertEquals(before + 1, windowCount());
    }

    // Proves a normal transaction raises no flags, gets the next file line, and updates the run summary.
    @Test
    void newTransactionGetsNextLineAndUpdatesSummary() throws Exception {
        int lastLine = Files.readAllLines(SAMPLES.resolve("transactions_1000.csv")).size();
        int validRows = JsonPath.read(summary, "$.validRows");

        mockMvc.perform(append("LIVE-NEW-1", "ACC_LIVE_1", "10.00", IN_RANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags", empty()))
                .andExpect(jsonPath("$.transaction.lineNumber").value(lastLine + 1))
                .andExpect(jsonPath("$.transaction.amountPaise").value(1000))
                .andExpect(jsonPath("$.bucketStats.sumPaise").value(greaterThanOrEqualTo(1000)))
                .andExpect(jsonPath("$.bucketIndex").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.screenMicros").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.updateMicros").value(greaterThanOrEqualTo(0)));
        mockMvc.perform(append("LIVE-NEW-2", "ACC_LIVE_1", "10.00", IN_RANGE))
                .andExpect(jsonPath("$.transaction.lineNumber").value(lastLine + 2));

        mockMvc.perform(get("/api/runs/" + runId))
                .andExpect(jsonPath("$.validRows").value(validRows + 2))
                .andExpect(jsonPath("$.duplicateStats.inserted")
                        .value(((Number) JsonPath.read(summary, "$.duplicateStats.inserted")).intValue() + 2));
    }

    // Proves an amount strictly above the run's threshold is flagged, and the threshold itself is unchanged.
    @Test
    void highAmountIsFlaggedAndThresholdIsNotRecomputed() throws Exception {
        long thresholdPaise = ((Number) JsonPath.read(summary, "$.threshold.valuePaise")).longValue();

        mockMvc.perform(append("LIVE-HIGH-1", "ACC_LIVE_1", Money.format(thresholdPaise + 1), IN_RANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].type").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$.thresholdNote").value(containsString("not recomputed")));
        mockMvc.perform(append("LIVE-HIGH-2", "ACC_LIVE_1", Money.format(thresholdPaise), IN_RANGE))
                .andExpect(jsonPath("$.flags", empty()));

        mockMvc.perform(get("/api/runs/" + runId))
                .andExpect(jsonPath("$.threshold.valuePaise").value(thresholdPaise));
        mockMvc.perform(get("/api/runs/" + runId + "/flags").param("type", "HIGH_AMOUNT").param("size", "200"))
                .andExpect(jsonPath("$.items[*].transactionId", hasItems("LIVE-HIGH-1")))
                .andExpect(jsonPath("$.items[?(@.transactionId == 'LIVE-HIGH-1')].accountId",
                        hasItems("ACC_LIVE_1")));
    }

    // Proves a blacklisted account is caught through the Bloom filter and its exact store.
    @Test
    void blacklistedAccountIsFlagged() throws Exception {
        String blacklisted = firstLine("blacklist_1000.csv", 1).trim();

        mockMvc.perform(append("LIVE-BL-1", blacklisted, "10.00", IN_RANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].type").value("BLACKLISTED"));
    }

    // Proves a JSON number is accepted for the amount, so a client need not quote it.
    @Test
    void numericAmountIsAccepted() throws Exception {
        mockMvc.perform(post("/api/runs/" + runId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"LIVE-NUM-1\",\"accountId\":\"ACC_LIVE_1\","
                                + "\"amount\":12.5,\"timestamp\":\"" + IN_RANGE + "\",\"merchant\":\"Shop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.amountPaise").value(1250));
    }

    // Proves each validation failure uses its RejectReason as the code and names the field to fix.
    @Test
    void invalidFieldsAreRejectedWithTheReasonAsCode() throws Exception {
        mockMvc.perform(append("LIVE-BAD-1", "ACC_LIVE_1", "ten", IN_RANGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.details.field").value("amount"));
        mockMvc.perform(append("LIVE-BAD-2", "ACC_LIVE_1", "10.005", IN_RANGE))
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        mockMvc.perform(append("LIVE-BAD-3", "ACC_LIVE_1", "0", IN_RANGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"))
                .andExpect(jsonPath("$.details.field").value("amount"));
        mockMvc.perform(append("LIVE-BAD-4", "ACC_LIVE_1", "10.00", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMESTAMP"))
                .andExpect(jsonPath("$.details.field").value("timestamp"));
        mockMvc.perform(post("/api/runs/" + runId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"LIVE-BAD-5\",\"amount\":\"10.00\","
                                + "\"timestamp\":\"" + IN_RANGE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.details.fields", hasItems("accountId", "merchant")));
    }

    // Proves a rejected append changes nothing: the ID is not remembered, so a retry is not a duplicate.
    @Test
    void timestampBeforeFirstBucketIsRejectedWithoutSideEffects() throws Exception {
        int validRows = JsonPath.read(summary, "$.validRows");

        mockMvc.perform(append("LIVE-EARLY-1", "ACC_LIVE_1", "10.00", "2025-12-31T00:00:00+05:30"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TIMESTAMP_BEFORE_RANGE"));

        mockMvc.perform(get("/api/runs/" + runId)).andExpect(jsonPath("$.validRows").value(validRows));
        mockMvc.perform(append("LIVE-EARLY-1", "ACC_LIVE_1", "10.00", IN_RANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags", empty()))
                .andExpect(jsonPath("$.transaction.lineNumber").value(
                        Files.readAllLines(SAMPLES.resolve("transactions_1000.csv")).size() + 1));
    }

    // Proves an append past the last bucket grows the index through the endpoint, leaving earlier windows as
    // they were.
    @Test
    void appendPastLastBucketGrowsTheIndex() throws Exception {
        long dataStart = ((Number) JsonPath.read(summary, "$.dataStart")).longValue();
        long dataEnd = ((Number) JsonPath.read(summary, "$.dataEnd")).longValue();
        List<Number> original = windowTotals(dataStart, dataEnd);

        mockMvc.perform(append("LIVE-FAR-1", "ACC_LIVE_1", "10.00", "2026-06-01T10:00:00+05:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketStats.count").value(1));

        long farEpoch = 1_780_288_200L;
        mockMvc.perform(get("/api/runs/" + runId)).andExpect(jsonPath("$.dataEnd").value(farEpoch));
        assertEquals(original, windowTotals(dataStart, dataEnd));
        assertEquals(List.of(1000, 1, 1000), windowTotals(farEpoch, farEpoch));
    }

    /** Returns the sum, count and max of one window. */
    private List<Number> windowTotals(long from, long to) throws Exception {
        String window = mockMvc.perform(get("/api/runs/" + runId + "/window")
                        .param("from", Long.toString(from)).param("to", Long.toString(to)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return List.of(JsonPath.read(window, "$.sumPaise"), JsonPath.read(window, "$.count"),
                JsonPath.read(window, "$.maxPaise"));
    }

    // Proves an unknown run and a body that is not JSON get the documented errors.
    @Test
    void unknownRunAndMalformedBodyAreRejected() throws Exception {
        mockMvc.perform(post("/api/runs/00000000-0000-0000-0000-000000000000/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        mockMvc.perform(post("/api/runs/" + runId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private int windowCount() throws Exception {
        String window = mockMvc.perform(get("/api/runs/" + runId + "/window")
                        .param("from", Long.toString(IN_RANGE_EPOCH)).param("to", Long.toString(IN_RANGE_EPOCH)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(window, "$.count");
    }

    private MockHttpServletRequestBuilder append(
            String transactionId, String accountId, String amount, String timestamp) {
        return post("/api/runs/" + runId + "/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionId\":\"" + transactionId + "\",\"accountId\":\"" + accountId
                        + "\",\"amount\":\"" + amount + "\",\"timestamp\":\"" + timestamp
                        + "\",\"merchant\":\"Live Shop\"}");
    }

    private static String firstLine(String fileName, int index) throws Exception {
        List<String> lines = Files.readAllLines(SAMPLES.resolve(fileName));
        return lines.get(index);
    }

    private static MockMultipartFile sample(String part, String fileName) throws Exception {
        return new MockMultipartFile(part, fileName, "text/csv", Files.readAllBytes(SAMPLES.resolve(fileName)));
    }
}
