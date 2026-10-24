package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// With no shared token configured the filter fails closed: no header value, empty or not, gets through.
@TestPropertySource(properties = "security.internal.token=")
class InternalTokenNotConfiguredTest extends GameServiceTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", INTERNAL_TOKEN})
    void everyInternalRequestIsRefused(String supplied) throws Exception {
        mvc.perform(get("/internal/games/1942").header(INTERNAL_HEADER, supplied)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/games/1942")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/games/ratings/prune").param("runId", "r").header(INTERNAL_HEADER, supplied))
                .andExpect(status().isUnauthorized());
    }
}
