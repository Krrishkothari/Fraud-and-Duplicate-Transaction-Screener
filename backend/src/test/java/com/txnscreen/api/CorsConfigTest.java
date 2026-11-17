package com.txnscreen.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Runs with two allowed origins, as ALLOWED_ORIGINS would set them in production. */
@SpringBootTest(properties = "app.allowed-origins=https://txnscreen.example, https://www.txnscreen.example")
class CorsConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Proves every listed origin, trimmed of spaces, may call the API and is named in the response.
    @Test
    void listedOriginsAreAllowed() throws Exception {
        for (String origin : new String[] {"https://txnscreen.example", "https://www.txnscreen.example"}) {
            mockMvc.perform(get("/api/health").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    // Proves a POST from the frontend passes the browser's preflight check.
    @Test
    void preflightForPostIsAllowed() throws Exception {
        mockMvc.perform(options("/api/runs/sample")
                        .header("Origin", "https://txnscreen.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://txnscreen.example"));
    }

    // Proves any other origin is refused, and so are methods the API does not use.
    @Test
    void otherOriginsAndMethodsAreRefused() throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/runs/sample")
                        .header("Origin", "https://txnscreen.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }
}
