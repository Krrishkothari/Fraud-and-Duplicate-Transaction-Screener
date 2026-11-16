package com.txnscreen.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.txnscreen.domain.FlagType;

/**
 * Screens the committed samples and edge cases through the run endpoints, and checks runs with an empty
 * blacklist and run IDs that do not exist.
 */
@SpringBootTest
class RunEdgeCaseTest {

    private static final Path SAMPLES = Path.of("..", "data", "samples");
    private static final int LABEL_DUPLICATE_COLUMN = 2;
    private static final int LABEL_BLACKLISTED_COLUMN = 3;
    private static final String UNKNOWN_RUN = "/api/runs/7f1f9a3e-0000-4000-8000-000000000000";
    private static final String TWO_TRANSACTIONS = "transaction_id,account_id,amount,timestamp,merchant\n"
            + "T1,ACC1,100.00,2026-01-05T09:00:00+05:30,Shop\n"
            + "T2,ACC2,250.50,2026-01-05T09:05:00+05:30,Shop\n";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves the edge case sample raises exactly its two DUPLICATE flags, each pointing at the line of the
    // first occurrence, even when the repeat writes the same amount differently.
    @Test
    void edgeCaseDuplicatesPointAtFirstLine() throws Exception {
        String id = createRun(multipart("/api/runs").file(sample("transactions", "edge_cases.csv")));

        mockMvc.perform(get("/api/runs/" + id + "/flags").param("type", "DUPLICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].transactionId", contains("EDG0002", "EDG0006")))
                .andExpect(jsonPath("$.items[*].lineNumber", contains(9, 16)))
                .andExpect(jsonPath("$.items[*].detail", contains("Duplicate of line 3", "Duplicate of line 7")));
    }

    // Proves that on the 10,000 row sample every labelled row has its flag, so there are no false negatives,
    // and no unlabelled row has a BLACKLISTED or DUPLICATE flag. HIGH_AMOUNT flags come from the amount
    // threshold rather than the labels, so they are expected and left out of the comparison.
    @Test
    void sampleFlagsMatchGroundTruthLabels() throws Exception {
        String id = createRun(multipart("/api/runs")
                .file(sample("transactions", "transactions_10000.csv"))
                .file(sample("blacklist", "blacklist_10000.csv")));
        mockMvc.perform(get("/api/runs/" + id))
                .andExpect(jsonPath("$.validRows").value(10_000))
                .andExpect(jsonPath("$.rejectedRows").value(0))
                .andExpect(jsonPath("$.flagCounts.HIGH_AMOUNT").value(greaterThan(0)));

        List<String> labels = Files.readAllLines(SAMPLES.resolve("labels_10000.csv"));
        Set<Integer> labelledBlacklisted = labelledLines(labels, LABEL_BLACKLISTED_COLUMN);
        Set<Integer> labelledDuplicates = labelledLines(labels, LABEL_DUPLICATE_COLUMN);
        Set<Integer> flaggedBlacklisted = flaggedLines(id, FlagType.BLACKLISTED);
        Set<Integer> flaggedDuplicates = flaggedLines(id, FlagType.DUPLICATE);

        assertAll(
                () -> assertEquals(Set.of(), minus(labelledBlacklisted, flaggedBlacklisted),
                        "lines labelled blacklisted without a BLACKLISTED flag"),
                () -> assertEquals(Set.of(), minus(labelledDuplicates, flaggedDuplicates),
                        "lines labelled duplicate without a DUPLICATE flag"),
                () -> assertEquals(Set.of(), minus(flaggedBlacklisted, labelledBlacklisted),
                        "BLACKLISTED flags on lines not labelled blacklisted"),
                () -> assertEquals(Set.of(), minus(flaggedDuplicates, labelledDuplicates),
                        "DUPLICATE flags on lines not labelled duplicate"));
    }

    // Proves a blacklist with no IDs, whether zero bytes or header only, still creates a run, with a warning
    // that no transaction was checked against it.
    @ParameterizedTest
    @ValueSource(strings = {"", "account_id\n"})
    void emptyBlacklistRunSucceedsWithWarning(String blacklist) throws Exception {
        mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", TWO_TRANSACTIONS))
                        .file(csv("blacklist", blacklist)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.flagCounts.BLACKLISTED").value(0))
                .andExpect(jsonPath("$.blacklistStats.inserted").value(0))
                .andExpect(jsonPath("$.warnings", contains(
                        "The blacklist is empty, so no transaction was checked against blacklisted accounts.")));
    }

    // Proves every endpoint under a run answers 404 RUN_NOT_FOUND, with the ID in the details, for a run ID
    // that was never created.
    @ParameterizedTest
    @MethodSource("runEndpoints")
    void unknownRunIsNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("No run with this ID exists. Runs expire 30 minutes after they are created."))
                .andExpect(jsonPath("$.details.runId").value("7f1f9a3e-0000-4000-8000-000000000000"));
    }

    static Stream<Named<MockHttpServletRequestBuilder>> runEndpoints() {
        return Stream.of(
                Named.of("summary", get(UNKNOWN_RUN)),
                Named.of("flags", get(UNKNOWN_RUN + "/flags")),
                Named.of("rejected rows", get(UNKNOWN_RUN + "/rejected")),
                Named.of("window", get(UNKNOWN_RUN + "/window").param("from", "0").param("to", "60")),
                Named.of("series", get(UNKNOWN_RUN + "/series")),
                Named.of("threshold", get(UNKNOWN_RUN + "/threshold")),
                Named.of("live append", post(UNKNOWN_RUN + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")));
    }

    private String createRun(MockMultipartHttpServletRequestBuilder upload) throws Exception {
        MvcResult result = mockMvc.perform(upload).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private Set<Integer> flaggedLines(String id, FlagType type) throws Exception {
        Set<Integer> lines = new TreeSet<>();
        int totalPages = 1;
        for (int page = 0; page < totalPages; page++) {
            String body = mockMvc.perform(get("/api/runs/" + id + "/flags")
                            .param("type", type.name())
                            .param("page", Integer.toString(page))
                            .param("size", Integer.toString(RunController.MAX_PAGE_SIZE)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            totalPages = JsonPath.read(body, "$.totalPages");
            List<Integer> pageLines = JsonPath.read(body, "$.items[*].lineNumber");
            lines.addAll(pageLines);
        }
        return lines;
    }

    private static Set<Integer> labelledLines(List<String> labels, int column) {
        Set<Integer> lines = new TreeSet<>();
        for (String label : labels.subList(1, labels.size())) {
            String[] fields = label.split(",");
            if (Boolean.parseBoolean(fields[column])) {
                lines.add(Integer.parseInt(fields[0]));
            }
        }
        return lines;
    }

    private static Set<Integer> minus(Set<Integer> from, Set<Integer> removed) {
        Set<Integer> remaining = new TreeSet<>(from);
        remaining.removeAll(removed);
        return remaining;
    }

    private static MockMultipartFile sample(String part, String fileName) throws Exception {
        return new MockMultipartFile(part, fileName, "text/csv", Files.readAllBytes(SAMPLES.resolve(fileName)));
    }

    private static MockMultipartFile csv(String part, String content) {
        return new MockMultipartFile(part, part + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
