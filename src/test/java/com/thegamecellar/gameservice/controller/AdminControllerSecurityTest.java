package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The admin prefix is guarded by ROLE_ADMIN, which the app's converter reads from the token's realm roles.
class AdminControllerSecurityTest extends GameServiceTestBase {

    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private static MockHttpServletRequestBuilder request(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        return "GET".equals(parts[0]) ? get(ADMIN_PREFIX + parts[1]) : post(ADMIN_PREFIX + parts[1]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /sync/status", "POST /sync", "POST /sync/quick", "POST /background-art",
            "POST /backfill-developers", "POST /prune-tags", "POST /derive-genres",
            "POST /backfill-release-data", "POST /migrate-rating-scale"})
    void withoutATokenOrWithARejectedOneEveryAdminEndpointAnswers401(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(request(endpoint).header(HttpHeaders.AUTHORIZATION, bearer("forged")))
                .andExpect(status().isUnauthorized());
        assertThat(adminSyncExecutor.isRunning()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /sync/status", "POST /sync", "POST /sync/quick", "POST /background-art",
            "POST /backfill-developers", "POST /prune-tags", "POST /derive-genres",
            "POST /backfill-release-data", "POST /migrate-rating-scale"})
    void aUserWithoutTheAdminRealmRoleGets403FromEveryAdminEndpoint(String endpoint) throws Exception {
        mvc.perform(request(endpoint).header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isForbidden());
        assertThat(adminSyncExecutor.isRunning()).isFalse();
    }

    @Test
    void theAdminRealmRoleOpensTheAdminPrefixAndSyncStatusReportsARunningJob() throws Exception {
        mvc.perform(get(ADMIN_PREFIX + "/sync/status").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        CountDownLatch release = new CountDownLatch(1);
        adminSyncExecutor.trySubmit("held", () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            mvc.perform(get(ADMIN_PREFIX + "/sync/status").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(jsonPath("$.running").value(true));
        } finally {
            release.countDown();
        }
    }
}
