package com.example.statementservice.infrastructure.crypto;

import com.example.statementservice.statement.signedlink.LinkSigner;
import com.example.statementservice.statement.signedlink.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSha256LinkSigner implements LinkSigner {

    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;

    public HmacSha256LinkSigner(String secretKey) {
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sign(String path, long expiresEpochSeconds, String method) {
        try {
            var data = method + "|" + path + "|" + expiresEpochSeconds;
            var mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            var raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new SignatureException("Failed to sign path", e);
        }
    }
}
