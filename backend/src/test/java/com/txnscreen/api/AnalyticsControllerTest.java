package com.txnscreen.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class AnalyticsControllerTest {

    private static final Path SAMPLES = Path.of("..", "data", "samples");

    /** 2026-01-01T09:00:00+05:30 and 2026-01-01T12:00:00+05:30, inside the 10K sample's range. */
    private static final long MORNING = 1_767_238_200L;
    private static final long NOON = 1_767_249_000L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves the series over the full range counts every valid row exactly once (the H06 done check).
    @Test
    void seriesCountsSumToValidRowsOnTenThousandSample() throws Exception {
        String summary = createRun("transactions_10000.csv", "blacklist_10000.csv");
        String id = JsonPath.read(summary, "$.id");
        int validRows = JsonPath.read(summary, "$.validRows");

        String series = mockMvc.perform(get("/api/runs/" + id + "/series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(120))
                .andExpect(jsonPath("$.clamped").value(false))
                .andReturn().getResponse().getContentAsString();
        List<Number> counts = JsonPath.read(series, "$.points[*].count");

        assertEquals(validRows, counts.stream().mapToLong(Number::longValue).sum());
        mockMvc.perform(get("/api/runs/" + id + "/window")
                        .param("from", JsonPath.read(summary, "$.dataStart").toString())
                        .param("to", JsonPath.read(summary, "$.dataEnd").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(validRows))
                .andExpect(jsonPath("$.algoMicros").value(greaterThanOrEqualTo(0)));
    }

    // Proves a window bound given as ISO-8601 with offset means the same instant as epoch seconds.
    @Test
    void isoAndEpochBoundsGiveTheSameWindow() throws Exception {
        String id = JsonPath.read(createRun("transactions_1000.csv", "blacklist_1000.csv"), "$.id");

        String byEpoch = mockMvc.perform(get("/api/runs/" + id + "/window")
                        .param("from", Long.toString(MORNING)).param("to", Long.toString(NOON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(MORNING))
                .andExpect(jsonPath("$.to").value(NOON))
                .andReturn().getResponse().getContentAsString();
        String byIso = mockMvc.perform(get("/api/runs/" + id + "/window")
                        .param("from", "2026-01-01T09:00:00+05:30").param("to", "2026-01-01T06:30:00Z"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals((Object) JsonPath.read(byEpoch, "$.sumPaise"), JsonPath.read(byIso, "$.sumPaise"));
        assertEquals((Object) JsonPath.read(byEpoch, "$.count"), JsonPath.read(byIso, "$.count"));
    }

    // Proves malformed bounds and out-of-range point counts are rejected with the documented codes.
    @Test
    void rejectsMalformedBoundsAndPointCounts() throws Exception {
        String runs = "/api/runs/" + JsonPath.read(createRun("transactions_1000.csv", "blacklist_1000.csv"), "$.id");

        mockMvc.perform(get(runs + "/window").param("from", "yesterday").param("to", Long.toString(NOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"))
                .andExpect(jsonPath("$.details.parameter").value("from"));
        mockMvc.perform(get(runs + "/window").param("from", Long.toString(NOON)).param("to", Long.toString(MORNING)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WINDOW"));
        mockMvc.perform(get(runs + "/series").param("points", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.parameter").value("points"));
        mockMvc.perform(get(runs + "/series").param("points", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.parameter").value("points"));
        mockMvc.perform(get("/api/runs/00000000-0000-0000-0000-000000000000/window")
                        .param("from", "0").param("to", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    private String createRun(String transactions, String blacklist) throws Exception {
        return mockMvc.perform(multipart("/api/runs")
                        .file(sample("transactions", transactions))
                        .file(sample("blacklist", blacklist)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analyticsBuildMicros").value(greaterThanOrEqualTo(0)))
                .andReturn().getResponse().getContentAsString();
    }

    private static MockMultipartFile sample(String part, String fileName) throws Exception {
        return new MockMultipartFile(part, fileName, "text/csv", Files.readAllBytes(SAMPLES.resolve(fileName)));
    }
}
