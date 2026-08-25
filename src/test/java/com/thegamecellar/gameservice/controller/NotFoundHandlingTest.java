package com.thegamecellar.gameservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotFoundHandlingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unmatchedPath_returns404_notInternalServerError() throws Exception {
        mvc.perform(get("/wp-admin").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/wp-admin"));
    }

    @Test
    void unmatchedMethodOnKnownPrefix_returns404() throws Exception {
        mvc.perform(post("/api/v1/games/does-not-exist/nope").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
