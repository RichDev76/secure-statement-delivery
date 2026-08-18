package com.example.statementservice.statement.signedlink.infrastructure;

import com.example.statementservice.statement.signedlink.DownloadUrlProvider;
import com.example.statementservice.statement.signedlink.SignedLinkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class ServletDownloadUrlProvider implements DownloadUrlProvider {

    private final SignedLinkProperties properties;

    @Override
    public String toAbsoluteUrl(String relativePath) {
        var externalBaseUrl = properties.getExternalBaseUrl();
        var base = externalBaseUrl != null
                ? externalBaseUrl
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        var trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + relativePath;
    }
}
