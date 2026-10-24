package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotFoundHandlingTest extends GameServiceTestBase {

    @Test
    void unmatchedPath_returns404_notInternalServerError() throws Exception {
        mvc.perform(get("/wp-admin").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/wp-admin"));
    }

    @Test
    void unmatchedMethodOnKnownPrefix_returns404() throws Exception {
        mvc.perform(post("/api/v1/games/does-not-exist/nope").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isNotFound());
    }
}
