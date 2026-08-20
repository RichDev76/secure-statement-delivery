package com.example.statementservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.statementservice.shared.Sha256Digest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** Shared upload/link/download steps for integration tests exercising the full HTTP stack. */
public final class UploadDownloadSteps {

    public record UploadedStatement(String statementId, String accountNumber, byte[] content) {}

    private UploadDownloadSteps() {}

    public static String uniqueAccountNumber(String prefix) {
        return prefix + String.format("%08d", System.currentTimeMillis() % 100000000L);
    }

    public static UploadedStatement uploadPdf(MockMvc mockMvc, String fileName, String accountNumber) throws Exception {
        var content = ("%PDF-1.4\n" + UUID.randomUUID() + "\n%%EOF").getBytes();
        var file = new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, content);
        var responseBody = mockMvc.perform(multipart("/api/v1/statements/upload")
                        .file(file)
                        .param("accountNumber", accountNumber)
                        .param("date", "2026-07-01")
                        .header("X-Message-Digest", Sha256Digest.hexOf(content))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Upload"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new UploadedStatement(JsonPath.read(responseBody, "$.statementId"), accountNumber, content);
    }

    public static UriComponents mintDownloadLink(MockMvc mockMvc, String statementId, String accountNumber)
            throws Exception {
        var responseBody = mockMvc.perform(get("/api/v1/statements/link/{statementId}", statementId)
                        .queryParam("accountNumber", accountNumber)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GenerateSignedLink"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String downloadLink = JsonPath.read(responseBody, "$.downloadLink");
        return UriComponentsBuilder.fromUriString(downloadLink).build();
    }

    public static MockHttpServletRequestBuilder downloadRequest(UriComponents linkUri) {
        return get(linkUri.getPath())
                .queryParam("expires", linkUri.getQueryParams().getFirst("expires"))
                .queryParam("linkId", linkUri.getQueryParams().getFirst("linkId"))
                .queryParam("signature", linkUri.getQueryParams().getFirst("signature"));
    }
}
