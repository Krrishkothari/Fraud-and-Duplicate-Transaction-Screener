package com.txnscreen.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.txnscreen.domain.Transaction;
import com.txnscreen.ingest.CsvTransactionParser;

/**
 * Tests GET /api/runs/{id}/threshold on runs made from the committed samples. Every expected value is found
 * independently of Quickselect, by sorting the sample's amounts and reading the nearest-rank index.
 */
@SpringBootTest
class ThresholdControllerTest {

    private static final Path SAMPLES = Path.of("..", "data", "samples");

    /** 2026-01-05T00:00:00+05:30 and 2026-01-05T23:59:59+05:30, one day inside the 1K sample's range. */
    private static final long DAY_START = 1_767_551_400L;
    private static final long DAY_END = 1_767_637_799L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves P99 and P95 over the whole run equal the nearest-rank values from sorting every amount.
    @Test
    void p99AndP95EqualSortedNearestRankValues() throws Exception {
        String id = createSampleRun();
        long[] sorted = sortedAmounts(sampleRows());

        for (double p : new double[] {99, 95}) {
            long expected = nearestRank(sorted, p);
            mockMvc.perform(get(thresholdUrl(id)).param("p", Double.toString(p)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.percentile").value(p))
                    .andExpect(jsonPath("$.valuePaise").value(expected))
                    .andExpect(jsonPath("$.n").value(sorted.length))
                    .andExpect(jsonPath("$.countAbove").value(countAbove(sorted, expected)))
                    .andExpect(jsonPath("$.clamped").value(false));
        }
    }

    // Proves p = 100 selects the largest amount, with nothing strictly above it.
    @Test
    void p100EqualsTheMaximum() throws Exception {
        long[] sorted = sortedAmounts(sampleRows());

        mockMvc.perform(get(thresholdUrl(createSampleRun())).param("p", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuePaise").value(sorted[sorted.length - 1]))
                .andExpect(jsonPath("$.countAbove").value(0));
    }

    // Proves a percentile outside (0, 100] is rejected as INVALID_CONFIG naming the field p (edge case).
    @Test
    void percentileOutsideRangeIsRejected() throws Exception {
        String id = createSampleRun();

        for (String p : new String[] {"0", "100.5", "-5"}) {
            mockMvc.perform(get(thresholdUrl(id)).param("p", p))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CONFIG"))
                    .andExpect(jsonPath("$.details.fieldErrors[0].field").value("p"));
        }
    }

    // Proves a windowed threshold uses only that window's amounts, equal to sorting just those amounts.
    @Test
    void windowedThresholdEqualsSortingOnlyThatWindow() throws Exception {
        String id = createSampleRun();
        long[] window = sortedAmounts(sampleRows().stream()
                .filter(t -> t.epochSeconds() >= DAY_START && t.epochSeconds() <= DAY_END)
                .toList());
        assertTrue(window.length > 0, "the test day must hold transactions");
        long expected = nearestRank(window, 99);

        mockMvc.perform(get(thresholdUrl(id)).param("p", "99")
                        .param("from", "2026-01-05T00:00:00+05:30")
                        .param("to", Long.toString(DAY_END)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuePaise").value(expected))
                .andExpect(jsonPath("$.n").value(window.length))
                .andExpect(jsonPath("$.countAbove").value(countAbove(window, expected)))
                .andExpect(jsonPath("$.effectiveFrom").value(DAY_START))
                .andExpect(jsonPath("$.effectiveTo").value(DAY_END));
    }

    // Proves a window inside the data range that holds no transaction is 422 EMPTY_WINDOW (edge case).
    @Test
    void emptyWindowIsUnprocessable() throws Exception {
        long gap = firstSecondWithoutTransaction(sampleRows());

        mockMvc.perform(get(thresholdUrl(createSampleRun()))
                        .param("from", Long.toString(gap)).param("to", Long.toString(gap)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMPTY_WINDOW"))
                .andExpect(jsonPath("$.details.effectiveFrom").value(gap));
    }

    // Proves a run with one transaction returns that amount for any percentile (edge case).
    @Test
    void singleTransactionRunReturnsItsAmountForAnyPercentile() throws Exception {
        String id = JsonPath.read(createRun(new MockMultipartFile("transactions", "one.csv", "text/csv",
                ("transaction_id,account_id,amount,timestamp,merchant\n"
                        + "T1,ACC1,734.25,2026-01-05T10:00:00+05:30,Grocery Store 001\n")
                        .getBytes(StandardCharsets.UTF_8)), null), "$.id");

        for (String p : new String[] {"0.01", "50", "99.9", "100"}) {
            mockMvc.perform(get(thresholdUrl(id)).param("p", p))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valuePaise").value(73_425))
                    .andExpect(jsonPath("$.n").value(1))
                    .andExpect(jsonPath("$.countAbove").value(0));
        }
    }

    // Proves the run's HIGH_AMOUNT flags are exactly the amounts above its threshold, which the endpoint
    // reproduces when p is left out, using the run's configured percentile.
    @Test
    void highAmountFlagCountEqualsCountAbove() throws Exception {
        String summary = createRun(sample("transactions", "transactions_1000.csv"),
                new MockMultipartFile("config", "", MediaType.APPLICATION_JSON_VALUE,
                        "{\"percentile\":95}".getBytes(StandardCharsets.UTF_8)));
        String id = JsonPath.read(summary, "$.id");
        int highAmountFlags = JsonPath.read(summary, "$.flagCounts.HIGH_AMOUNT");
        int summaryCountAbove = JsonPath.read(summary, "$.threshold.countAbove");
        long[] sorted = sortedAmounts(sampleRows());

        assertEquals(countAbove(sorted, nearestRank(sorted, 95)), summaryCountAbove);
        assertEquals(summaryCountAbove, highAmountFlags);
        mockMvc.perform(get(thresholdUrl(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentile").value(95.0))
                .andExpect(jsonPath("$.countAbove").value(highAmountFlags))
                .andExpect(jsonPath("$.valuePaise").value((Object) JsonPath.read(summary, "$.threshold.valuePaise")));
        mockMvc.perform(get("/api/runs/" + id + "/flags").param("type", "HIGH_AMOUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(highAmountFlags));
    }

    private String createSampleRun() throws Exception {
        return JsonPath.read(createRun(sample("transactions", "transactions_1000.csv"), null), "$.id");
    }

    private String createRun(MockMultipartFile transactions, MockMultipartFile config) throws Exception {
        var request = multipart("/api/runs").file(transactions);
        if (config != null) {
            request.file(config);
        }
        return mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String thresholdUrl(String id) {
        return "/api/runs/" + id + "/threshold";
    }

    private static MockMultipartFile sample(String part, String fileName) throws Exception {
        return new MockMultipartFile(part, fileName, "text/csv", Files.readAllBytes(SAMPLES.resolve(fileName)));
    }

    /** The 1K sample's valid rows, sorted by time, read with the app's own parser. */
    private static List<Transaction> sampleRows() throws Exception {
        try (InputStream input = Files.newInputStream(SAMPLES.resolve("transactions_1000.csv"))) {
            return new CsvTransactionParser().parse(input).valid();
        }
    }

    private static long[] sortedAmounts(List<Transaction> rows) {
        long[] amounts = rows.stream().mapToLong(Transaction::amountPaise).toArray();
        Arrays.sort(amounts);
        return amounts;
    }

    /** The k-th smallest of the sorted values, with k = ceil(p / 100 * n), written out plainly. */
    private static long nearestRank(long[] sorted, double p) {
        int k = (int) Math.ceil(p / 100 * sorted.length);
        return sorted[Math.max(1, k) - 1];
    }

    private static long countAbove(long[] values, long threshold) {
        return Arrays.stream(values).filter(v -> v > threshold).count();
    }

    /** A second inside the data range that no transaction falls on. */
    private static long firstSecondWithoutTransaction(List<Transaction> sortedRows) {
        for (int i = 0; i + 1 < sortedRows.size(); i++) {
            long next = sortedRows.get(i).epochSeconds() + 1;
            if (sortedRows.get(i + 1).epochSeconds() > next) {
                return next;
            }
        }
        throw new IllegalStateException("the sample has no gap between transactions");
    }
}
