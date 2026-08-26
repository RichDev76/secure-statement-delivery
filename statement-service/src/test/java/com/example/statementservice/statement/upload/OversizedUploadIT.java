package com.example.statementservice.statement.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statementservice.AbstractIntegrationTest;
import com.example.statementservice.infrastructure.crypto.Sha256ContentDigest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.unit.DataSize;

/**
 * Runs over real HTTP deliberately: MockMvc bypasses the servlet multipart parser, so the
 * max-file-size limit never fires under it. Uses the production max-swallow-size from
 * application.yml, so this test pins both the handler contract and the connector tuning that
 * makes the 413 reachable (Tomcat's 2MB default would reset the connection instead).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OversizedUploadIT extends AbstractIntegrationTest {

    private static final int ONE_MEBIBYTE = 1024 * 1024;

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${server.tomcat.max-swallow-size}")
    private DataSize maxSwallowSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    // Real-HTTP requests cannot use MockMvc's jwt() post-processor; this decoder accepts any
    // bearer token and grants the Upload role.
    @TestConfiguration
    static class StubJwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("oversized-upload-it")
                    .claim("roles", List.of("Upload"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    @Test
    void GivenPdfLargerThanConfiguredLimit_WhenUploadedOverHttp_ThenReturns413ProblemDetail() {
        // Given: 11 MiB, one over the configured 10MB max-file-size
        var oversized = new byte[11 * ONE_MEBIBYTE];
        oversized[0] = '%';
        oversized[1] = 'P';
        oversized[2] = 'D';
        oversized[3] = 'F';

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(oversized) {
            @Override
            public String getFilename() {
                return "oversized.pdf";
            }
        });
        body.add("accountNumber", "123456789");
        body.add("date", "2026-07-01");

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth("test-token");
        headers.set("X-Message-Digest", new Sha256ContentDigest().hexOf(oversized));

        // When
        var response =
                restTemplate.postForEntity("/api/v1/statements/upload", new HttpEntity<>(body, headers), String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) JsonPath.read(response.getBody(), "$.errorCode")).isEqualTo("UPLOAD_TOO_LARGE");
    }

    @Test
    void GivenConnectorConfiguration_WhenComparingLimits_ThenSwallowSizeExceedsMaxRequestSize() {
        // Then: if max-request-size is ever raised past max-swallow-size, Tomcat resets the
        // connection on oversized uploads instead of delivering the 413 problem detail.
        assertThat(maxSwallowSize.toBytes()).isGreaterThan(maxRequestSize.toBytes());
    }
}
