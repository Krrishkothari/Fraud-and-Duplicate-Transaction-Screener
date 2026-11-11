package com.txnscreen.api;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class RunControllerTest {

    private static final String HEADER = "transaction_id,account_id,amount,timestamp,merchant\n";
    private static final String TRANSACTIONS = HEADER
            + "T1,ACC1,100.00,2026-01-05T09:00:00+05:30,Shop\n"
            + "T2,ACC9,250.50,2026-01-05T09:05:00+05:30,Shop\n"
            + "T1,ACC1,100.00,2026-01-05T09:10:00+05:30,Shop\n"
            + "T3,ACC2,abc,2026-01-05T09:15:00+05:30,Shop\n"
            + "T4,ACC2,10.00,2026-01-05T09:20:00+05:30,Shop\n";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves an upload is screened and summarised, with a Location header for the new run.
    @Test
    void uploadCreatesRun() throws Exception {
        mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", TRANSACTIONS))
                        .file(csv("blacklist", "account_id\nACC9\nACC8\n"))
                        .file(json("config", "{\"hashCount\":4}")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/runs/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.validRows").value(4))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.flagCounts.BLACKLISTED").value(1))
                .andExpect(jsonPath("$.flagCounts.DUPLICATE").value(1))
                .andExpect(jsonPath("$.flagCounts.HIGH_AMOUNT").value(0))
                .andExpect(jsonPath("$.blacklistStats.inserted").value(2))
                .andExpect(jsonPath("$.duplicateStats.checks").value(4))
                .andExpect(jsonPath("$.duplicateStats.hashCount").value(4))
                .andExpect(jsonPath("$.duplicateStats.suggestedBits").value(nullValue()))
                .andExpect(jsonPath("$.dataStart").value(1_767_583_800L))
                .andExpect(jsonPath("$.dataEnd").value(1_767_585_000L))
                .andExpect(jsonPath("$.config.hashCount").value(4))
                .andExpect(jsonPath("$.config.targetFpr").value(0.01))
                .andExpect(jsonPath("$.config.bucketSeconds").value(60))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.algoMicros").value(greaterThanOrEqualTo(0)));
    }

    // Proves the blacklist and config parts are optional, and a missing blacklist is warned about.
    @Test
    void uploadWithoutOptionalPartsUsesDefaults() throws Exception {
        mockMvc.perform(multipart("/api/runs").file(csv("transactions", TRANSACTIONS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.targetFpr").value(0.01))
                .andExpect(jsonPath("$.flagCounts.BLACKLISTED").value(0))
                .andExpect(jsonPath("$.warnings[0]").value(endsWith("checked against blacklisted accounts.")));
    }

    // Proves a run can be read back, and its flags and rejected rows are paged and filtered.
    @Test
    void runCanBeReadAndPaged() throws Exception {
        String id = createRun();

        mockMvc.perform(get("/api/runs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.validRows").value(4));
        mockMvc.perform(get("/api/runs/" + id + "/flags").param("type", "DUPLICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].transactionId").value("T1"))
                .andExpect(jsonPath("$.items[0].lineNumber").value(4))
                .andExpect(jsonPath("$.items[0].detail").value("Duplicate of line 2"));
        mockMvc.perform(get("/api/runs/" + id + "/flags").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("DUPLICATE"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/runs/" + id + "/rejected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineNumber").value(5))
                .andExpect(jsonPath("$.items[0].reason").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    // Proves a page past the end is empty, not an error.
    @Test
    void pagePastEndIsEmpty() throws Exception {
        mockMvc.perform(get("/api/runs/" + createRun() + "/flags").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    // Proves out of range paging parameters and unknown flag types are refused.
    @Test
    void badPagingIsRefused() throws Exception {
        String flags = "/api/runs/" + createRun() + "/flags";

        mockMvc.perform(get(flags).param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details.parameter").value("size"));
        mockMvc.perform(get(flags).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.parameter").value("page"));
        mockMvc.perform(get(flags).param("type", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.parameter").value("type"));
    }

    // Proves unknown and malformed run IDs are both 404 RUN_NOT_FOUND.
    @Test
    void unknownRunIsNotFound() throws Exception {
        for (String id : new String[] {"7f1f9a3e-0000-4000-8000-000000000000", "not-a-uuid"}) {
            mockMvc.perform(get("/api/runs/" + id + "/rejected"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
                    .andExpect(jsonPath("$.details.runId").value(id));
        }
    }

    // Proves a file where every row is invalid is 422 with at most the first 50 rejected rows.
    @Test
    void fileWithNoValidRowsIsUnprocessable() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 60; i++) {
            csv.append("T").append(i).append(",ACC1,0,2026-01-05T09:00:00Z,Shop\n");
        }

        mockMvc.perform(multipart("/api/runs").file(csv("transactions", csv.toString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_VALID_ROWS"))
                .andExpect(jsonPath("$.details.totalRows").value(60))
                .andExpect(jsonPath("$.details.rejectedRows", hasSize(50)))
                .andExpect(jsonPath("$.details.rejectedRows[0].lineNumber").value(2))
                .andExpect(jsonPath("$.details.rejectedRows[0].reason").value("AMOUNT_NOT_POSITIVE"));
    }

    // Proves an invalid config part is refused with the failing field named.
    @Test
    void invalidConfigPartIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", TRANSACTIONS))
                        .file(json("config", "{\"targetFpr\":1.5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIG"))
                .andExpect(jsonPath("$.details.fieldErrors[0].field").value("targetFpr"));
    }

    // Proves upload errors return the standard error shape instead of a server error.
    @Test
    void malformedUploadsAreBadRequests() throws Exception {
        mockMvc.perform(multipart("/api/runs").file(csv("blacklist", "account_id\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.parameter").value("transactions"));
        mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", TRANSACTIONS))
                        .file(json("config", "{not json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(multipart("/api/runs").file(csv("transactions", "wrong,header\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_HEADER"));
        mockMvc.perform(post("/api/runs").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // Proves a sample run generates and screens the requested number of rows, with defaults filled in.
    @Test
    void sampleRunIsGenerated() throws Exception {
        mockMvc.perform(post("/api/runs/sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"size\":2000,\"seed\":7,\"config\":{\"hashCount\":5}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(2000))
                .andExpect(jsonPath("$.rejectedRows").value(0))
                .andExpect(jsonPath("$.flagCounts.DUPLICATE").value(greaterThan(0)))
                .andExpect(jsonPath("$.flagCounts.BLACKLISTED").value(greaterThan(0)))
                .andExpect(jsonPath("$.duplicateStats.hashCount").value(5))
                .andExpect(jsonPath("$.config.seed").value(42));
    }

    // Proves sample size and rates outside their ranges are refused with every failing field named.
    @Test
    void sampleRequestOutOfRangeIsRefused() throws Exception {
        mockMvc.perform(post("/api/runs/sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"size\":200001,\"duplicateRate\":0.3,\"blacklistRate\":-0.1,"
                                + "\"config\":{\"percentile\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIG"))
                .andExpect(jsonPath("$.details.fieldErrors", hasSize(4)));
        mockMvc.perform(post("/api/runs/sample").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors[0].field").value("size"));
    }

    private String createRun() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/runs")
                        .file(csv("transactions", TRANSACTIONS))
                        .file(csv("blacklist", "account_id\nACC9\nACC8\n")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile json(String name, String content) {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }
}
