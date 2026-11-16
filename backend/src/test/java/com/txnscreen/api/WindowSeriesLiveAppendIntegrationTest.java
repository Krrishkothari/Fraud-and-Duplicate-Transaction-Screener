package com.txnscreen.api;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.txnscreen.domain.Money;

/**
 * Drives the window, series and live append endpoints end to end on a fresh run of the committed 1,000 row
 * sample, so every answer goes through the controllers, the run store and the segment tree together.
 */
@SpringBootTest
class WindowSeriesLiveAppendIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "data", "samples", "transactions_1000.csv");
    private static final Path BLACKLIST = Path.of("..", "data", "samples", "blacklist_1000.csv");
    private static final int AMOUNT_COLUMN = 2;
    private static final long ONE_HOUR = 3_600;
    private static final long ONE_DAY = 86_400;

    /** A time in the middle of the sample's 30 days. */
    private static final String MID_RANGE = "2026-01-15T14:30:00+05:30";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private String runs;
    private int validRows;
    private long dataStart;
    private long dataEnd;

    @BeforeEach
    void createRun() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String summary = mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", SAMPLE))
                        .file(csv("blacklist", BLACKLIST)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        runs = "/api/runs/" + JsonPath.read(summary, "$.id");
        validRows = JsonPath.read(summary, "$.validRows");
        dataStart = ((Number) JsonPath.read(summary, "$.dataStart")).longValue();
        dataEnd = ((Number) JsonPath.read(summary, "$.dataEnd")).longValue();
    }

    // 1. Proves the full-range window matches the run summary, and its sum and max match the sample file itself.
    @Test
    void fullRangeWindowEqualsSummaryTotals() throws Exception {
        List<Long> amounts = sampleAmounts();
        long sum = amounts.stream().mapToLong(Long::longValue).sum();
        long max = amounts.stream().mapToLong(Long::longValue).max().orElseThrow();

        window(dataStart, dataEnd)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(validRows))
                .andExpect(jsonPath("$.count").value(amounts.size()))
                .andExpect(jsonPath("$.sumPaise").value(sum))
                .andExpect(jsonPath("$.maxPaise").value(max))
                .andExpect(jsonPath("$.effectiveFrom").value(dataStart))
                .andExpect(jsonPath("$.effectiveTo").value(dataEnd))
                .andExpect(jsonPath("$.clamped").value(false));
    }

    // 2. Proves a window whose start is after its end is rejected, for both endpoints.
    @Test
    void fromAfterToIsInvalidWindow() throws Exception {
        window(dataEnd, dataStart)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"))
                .andExpect(jsonPath("$.details.from").value(dataEnd))
                .andExpect(jsonPath("$.details.to").value(dataStart));
        mockMvc.perform(get(runs + "/series").param("from", Long.toString(dataEnd))
                        .param("to", Long.toString(dataStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"));
    }

    // 3. Proves a window that ends before the data starts is rejected and reports both ranges.
    @Test
    void windowEntirelyBeforeDataIsOutOfRange() throws Exception {
        long from = dataStart - ONE_DAY;
        long to = dataStart - 1;

        window(from, to)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WINDOW_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.details.requestedFrom").value(from))
                .andExpect(jsonPath("$.details.requestedTo").value(to))
                .andExpect(jsonPath("$.details.dataStart").value(dataStart))
                .andExpect(jsonPath("$.details.dataEnd").value(dataEnd));
    }

    // 4. Proves a window reaching before the data is clamped, and answers exactly as the clamped window does.
    @Test
    void windowPartlyOutsideIsClamped() throws Exception {
        long to = dataStart + ONE_DAY;
        String inside = window(dataStart, to).andReturn().getResponse().getContentAsString();

        window(dataStart - ONE_HOUR, to)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clamped").value(true))
                .andExpect(jsonPath("$.from").value(dataStart - ONE_HOUR))
                .andExpect(jsonPath("$.effectiveFrom").value(dataStart))
                .andExpect(jsonPath("$.effectiveTo").value(to))
                .andExpect(jsonPath("$.count").value((Object) JsonPath.read(inside, "$.count")))
                .andExpect(jsonPath("$.sumPaise").value((Object) JsonPath.read(inside, "$.sumPaise")));
        window(dataEnd - ONE_HOUR, dataEnd + ONE_DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clamped").value(true))
                .andExpect(jsonPath("$.effectiveTo").value(dataEnd));
    }

    // 5. Proves a malformed timestamp is rejected with 400 and names the parameter, on both endpoints.
    @Test
    void malformedTimestampIsRejected() throws Exception {
        mockMvc.perform(get(runs + "/window").param("from", Long.toString(dataStart))
                        .param("to", "2026-13-45T00:00:00+05:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"))
                .andExpect(jsonPath("$.details.parameter").value("to"));
        mockMvc.perform(get(runs + "/series").param("from", "2026-01-15T14:30:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"))
                .andExpect(jsonPath("$.details.parameter").value("from"));
    }

    // 6. Proves 500 points is the largest series allowed and 501 is rejected.
    @Test
    void seriesWithTooManyPointsIsRejected() throws Exception {
        mockMvc.perform(get(runs + "/series").param("points", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details.parameter").value("points"));
        mockMvc.perform(get(runs + "/series").param("points", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(lessThanOrEqualTo(500)));
    }

    // 7. Proves the series windows are contiguous and their counts and sums add up to the whole run.
    @Test
    void seriesCountsSumToValidRows() throws Exception {
        long totalSum = sampleAmounts().stream().mapToLong(Long::longValue).sum();

        for (String points : List.of("120", "500")) {
            String series = mockMvc.perform(get(runs + "/series").param("points", points))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveFrom").value(dataStart))
                    .andExpect(jsonPath("$.effectiveTo").value(dataEnd))
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Number>> windows = JsonPath.read(series, "$.points");

            assertEquals(validRows, windows.stream().mapToLong(w -> w.get("count").longValue()).sum());
            assertEquals(totalSum, windows.stream().mapToLong(w -> w.get("sumPaise").longValue()).sum());
            for (int i = 1; i < windows.size(); i++) {
                assertEquals(windows.get(i - 1).get("to").longValue() + 1, windows.get(i).get("from").longValue(),
                        "window " + i + " of " + points);
            }
        }
    }

    // 8. Proves a new live transaction raises its window's count by one and its sum by the amount.
    @Test
    void liveAppendRaisesWindowCount() throws Exception {
        long ts = epoch(MID_RANGE);
        List<Long> before = totals(ts, ts);

        append("LIVE-H11-NEW", "ACC_LIVE_H11", "250.75", MID_RANGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.length()").value(0))
                .andExpect(jsonPath("$.bucketStats.count").value(before.get(1) + 1));

        List<Long> after = totals(ts, ts);
        assertEquals(before.get(0) + 25_075, after.get(0));
        assertEquals(before.get(1) + 1, after.get(1));
        assertEquals(Math.max(before.get(2), 25_075), after.get(2));
        assertEquals(validRows + 1L, totals(dataStart, dataEnd).get(1));
    }

    // 9. Proves an ID from the file, and an ID added live, are both flagged DUPLICATE of their first line.
    @Test
    void liveAppendWithExistingIdIsDuplicate() throws Exception {
        List<String> lines = Files.readAllLines(SAMPLE);
        String fileId = lines.get(500).split(",")[0];
        int firstLine = firstLineOf(lines, fileId);

        append(fileId, "ACC_LIVE_H11", "99.00", MID_RANGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].type").value("DUPLICATE"))
                .andExpect(jsonPath("$.flags[0].detail").value("Duplicate of line " + firstLine));

        int liveLine = ((Number) JsonPath.read(append("LIVE-H11-TWICE", "ACC_LIVE_H11", "10.00", MID_RANGE)
                .andExpect(jsonPath("$.flags.length()").value(0))
                .andReturn().getResponse().getContentAsString(), "$.transaction.lineNumber")).intValue();
        append("LIVE-H11-TWICE", "ACC_LIVE_H11", "10.00", MID_RANGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].type").value("DUPLICATE"))
                .andExpect(jsonPath("$.flags[0].detail").value("Duplicate of line " + liveLine));
    }

    // 10. Proves a timestamp before the first bucket is rejected with 422 and leaves every total unchanged.
    @Test
    void liveAppendBeforeDataIsRejected() throws Exception {
        List<Long> before = totals(dataStart, dataEnd);

        append("LIVE-H11-EARLY", "ACC_LIVE_H11", "10.00", "2025-12-01T09:00:00+05:30")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TIMESTAMP_BEFORE_RANGE"))
                .andExpect(jsonPath("$.details.firstBucketStart").value(lessThanOrEqualTo((int) dataStart)));

        assertEquals(before, totals(dataStart, dataEnd));
    }

    // 11. Proves an append far past the end grows the index, and every earlier window of the chart is unchanged.
    @Test
    void liveAppendFarPastEndGrowsIndex() throws Exception {
        String far = "2026-03-15T12:00:00+05:30";
        long farEpoch = epoch(far);
        List<Object> seriesBefore = seriesPoints(dataStart, dataEnd);

        append("LIVE-H11-FAR", "ACC_LIVE_H11", "10.00", far)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketStats.count").value(1));

        assertEquals(seriesBefore, seriesPoints(dataStart, dataEnd));
        assertEquals(List.of(1_000L, 1L, 1_000L), totals(farEpoch, farEpoch));
        assertEquals(validRows + 1L, totals(dataStart, farEpoch).get(1));
        mockMvc.perform(get(runs)).andExpect(jsonPath("$.dataEnd").value(farEpoch));
    }

    private ResultActions window(long from, long to) throws Exception {
        return mockMvc.perform(get(runs + "/window").param("from", Long.toString(from))
                .param("to", Long.toString(to)));
    }

    /** Returns the sum, count and max of one window. */
    private List<Long> totals(long from, long to) throws Exception {
        String window = window(from, to).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return List.of(((Number) JsonPath.read(window, "$.sumPaise")).longValue(),
                ((Number) JsonPath.read(window, "$.count")).longValue(),
                ((Number) JsonPath.read(window, "$.maxPaise")).longValue());
    }

    private List<Object> seriesPoints(long from, long to) throws Exception {
        String series = mockMvc.perform(get(runs + "/series").param("from", Long.toString(from))
                        .param("to", Long.toString(to)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(series, "$.points");
    }

    private ResultActions append(String transactionId, String accountId, String amount, String timestamp)
            throws Exception {
        return mockMvc.perform(post(runs + "/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionId\":\"" + transactionId + "\",\"accountId\":\"" + accountId
                        + "\",\"amount\":\"" + amount + "\",\"timestamp\":\"" + timestamp
                        + "\",\"merchant\":\"Live Shop\"}"));
    }

    private static List<Long> sampleAmounts() throws Exception {
        List<String> lines = Files.readAllLines(SAMPLE);
        return lines.subList(1, lines.size()).stream()
                .map(line -> Money.parseToPaise(line.split(",")[AMOUNT_COLUMN]))
                .toList();
    }

    /** Returns the 1-based file line of the first row with this transaction ID. */
    private static int firstLineOf(List<String> lines, String transactionId) {
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).split(",")[0].equals(transactionId)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("No row with ID " + transactionId);
    }

    private static long epoch(String isoTimestamp) {
        return OffsetDateTime.parse(isoTimestamp).toEpochSecond();
    }

    private static MockMultipartFile csv(String part, Path file) throws Exception {
        return new MockMultipartFile(part, file.getFileName().toString(), "text/csv", Files.readAllBytes(file));
    }
}
