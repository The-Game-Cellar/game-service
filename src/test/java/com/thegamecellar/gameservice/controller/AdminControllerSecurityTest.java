package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.service.AdminSyncExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminSyncExecutor adminSyncExecutor;

    @Test
    void adminEndpoint_withoutAuthentication_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/sync/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withoutAdminRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/admin/sync/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withAdminRole_returns200() throws Exception {
        when(adminSyncExecutor.isRunning()).thenReturn(false);

        mvc.perform(get("/api/v1/admin/sync/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
