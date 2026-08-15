package com.example.statementservice.statement.signedlink;

public interface DownloadUrlProvider {

    String toAbsoluteUrl(String relativePath);
}
