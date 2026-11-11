package com.txnscreen.api;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BloomControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves the plan for one million keys at 1 percent is the textbook m and k.
    @Test
    void planForOneMillionKeysAtOnePercent() throws Exception {
        plan("n", "1000000", "p", "0.01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(1_000_000))
                .andExpect(jsonPath("$.p").value(0.01))
                .andExpect(jsonPath("$.bits").value(9_585_059))
                .andExpect(jsonPath("$.hashCount").value(7))
                .andExpect(jsonPath("$.memoryBytes").value(1_198_136))
                .andExpect(jsonPath("$.theoreticalFpr", closeTo(0.01, 0.0005), Double.class));
    }

    // Proves a rate of 0 is refused, naming p.
    @Test
    void zeroRateIsRefused() throws Exception {
        expectInvalid(plan("n", "1000", "p", "0"), "p");
    }

    // Proves a rate of 1 is refused, naming p.
    @Test
    void rateOfOneIsRefused() throws Exception {
        expectInvalid(plan("n", "1000", "p", "1"), "p");
    }

    // Proves zero keys is refused, naming n.
    @Test
    void zeroKeysIsRefused() throws Exception {
        expectInvalid(plan("n", "0", "p", "0.01"), "n");
    }

    // Proves a hash count above 30 is refused, naming k.
    @Test
    void hashCountAboveThirtyIsRefused() throws Exception {
        expectInvalid(plan("n", "1000", "p", "0.01", "k", "31"), "k");
    }

    // Proves a plan needing more than the 8 MB cap is refused: 10 million keys at 1 percent need 95,850,584 bits.
    @Test
    void planAboveCapIsRefused() throws Exception {
        expectInvalid(plan("n", "10000000", "p", "0.01"), "n");
    }

    // Proves a given k replaces the optimal one, and the reported rate is the one that k gives.
    @Test
    void givenHashCountChangesReportedRate() throws Exception {
        plan("n", "1000000", "p", "0.01", "k", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bits").value(9_585_059))
                .andExpect(jsonPath("$.hashCount").value(2))
                .andExpect(jsonPath("$.theoreticalFpr", closeTo(0.0355, 0.0001), Double.class));
    }

    private ResultActions plan(String... params) throws Exception {
        var request = get("/api/bloom/plan");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private static void expectInvalid(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIG"))
                .andExpect(jsonPath("$.details.fieldErrors[0].field").value(field));
    }
}
