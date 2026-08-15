package com.example.statementservice.statement.signedlink;

public interface LinkSigner {

    String sign(String path, long expiresEpochSeconds, String method, String nonce);

    boolean verify(String signature, String path, long expiresEpochSeconds, String method, String nonce);
}
