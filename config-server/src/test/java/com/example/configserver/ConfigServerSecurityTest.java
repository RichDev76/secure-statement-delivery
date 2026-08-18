package com.example.configserver;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.profiles.active=native",
            "spring.cloud.config.server.native.search-locations=classpath:/",
            "spring.security.user.name=config-user",
            "spring.security.user.password=config-pass"
        })
@AutoConfigureMockMvc
class ConfigServerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenNoCredentials_WhenFetchingConfig_ThenReturns401() throws Exception {
        // When / Then
        mockMvc.perform(get("/statement-service/develop")).andExpect(status().isUnauthorized());
    }

    @Test
    void GivenValidBasicCredentials_WhenFetchingConfig_ThenReturns200() throws Exception {
        // When / Then
        mockMvc.perform(get("/statement-service/develop").with(httpBasic("config-user", "config-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void GivenNoCredentials_WhenCheckingHealth_ThenReturns200() throws Exception {
        // When / Then: container and compose healthchecks probe without credentials
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
