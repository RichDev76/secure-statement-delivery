package com.example.statementservice.statement.signedlink.infrastructure;

import com.example.statementservice.statement.signedlink.DownloadUrlProvider;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class ServletDownloadUrlProvider implements DownloadUrlProvider {

    @Value("${statement.api.download-path:/api/v1/statements/download/}")
    private String downloadPath;

    @Override
    public String downloadBaseUrl(String fileName) {
        var base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        var trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(trimmed + downloadPath + fileName).toString();
    }
}
