package com.example.statementservice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.statementservice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@AutoConfigureMockMvc
class ErrorContractIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GivenApplicationContext_WhenStarted_ThenBootProblemDetailsAdviceIsAbsent() {
        // Given / When: context started with the custom advice chain registered

        // Then
        assertThat(applicationContext.getBeanNamesForType(ResponseEntityExceptionHandler.class))
                .as("Boot's ProblemDetailsExceptionHandler must not be registered; it shadows "
                        + "the custom advice chain (set spring.mvc.problemdetails.enabled=false)")
                .isEmpty();

        assertThat(environment.getProperty("spring.mvc.problemdetails.enabled", Boolean.class, false))
                .as("spring.mvc.problemdetails.enabled must be false in every profile")
                .isFalse();
    }

    // The download endpoint's generated @RequestMapping only produces application/octet-stream and
    // application/problem+json (§3.5). ExceptionHandlerExceptionResolver negotiates content type
    // from that pair when a handler returns a bare ProblemDetail (not a pinned ResponseEntity) - if
    // a future OpenAPI edit ever drops application/problem+json from that list, this regresses to a
    // 406 or a broken content type instead of a correctly negotiated error body.
    @Test
    void GivenUnknownSignedLinkOnDownloadEndpoint_WhenRequestFails_ThenProblemJsonIsNegotiatedNotOctetStream()
            throws Exception {
        // Given / When
        var result = mockMvc.perform(get("/api/v1/statements/download/unknown-file.pdf.enc")
                        .param("expires", "9999999999")
                        .param("signature", "does-not-exist-in-the-database"))
                .andReturn();

        // Then
        assertThat(result.getResponse().getContentType())
                .as("download-endpoint error responses must negotiate application/problem+json, "
                        + "never the endpoint's octet-stream success type")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }
}
