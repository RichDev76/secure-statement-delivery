package com.example.statementservice.statement.signedlink.infrastructure;

import com.example.statementservice.statement.signedlink.DownloadUrlProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class ServletDownloadUrlProvider implements DownloadUrlProvider {

    @Override
    public String toAbsoluteUrl(String relativePath) {
        var base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        var trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + relativePath;
    }
}
