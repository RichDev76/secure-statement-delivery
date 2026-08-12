package com.example.statementservice.infrastructure.crypto;

import static org.junit.jupiter.api.Assertions.*;

import com.example.statementservice.statement.signedlink.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HmacSha256LinkSigner Tests")
class HmacSha256LinkSignerTest {

    private HmacSha256LinkSigner hmacSha256LinkSigner;
    private String secretKey;

    @BeforeEach
    void setUp() {
        secretKey = "test-secret-key-12345";
        hmacSha256LinkSigner = new HmacSha256LinkSigner(secretKey);
    }

    @Test
    @DisplayName("Should create HmacSha256LinkSigner with secret key")
    void testConstructor() {
        var util = new HmacSha256LinkSigner("my-secret");
        assertNotNull(util);
    }

    @Test
    @DisplayName("Should generate signature for valid inputs")
    void testSignWithMethod_ValidInputs() {
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertFalse(signature.endsWith("="));
    }

    @Test
    @DisplayName("Should generate consistent signatures for same inputs")
    void testSignWithMethod_Consistency() {
        var path = "/download/file.pdf";
        var expires = 9876543210L;
        var method = "GET";
        var signature1 = hmacSha256LinkSigner.sign(path, expires, method);
        var signature2 = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different paths")
    void testSignWithMethod_DifferentPaths() {
        var expires = 1234567890L;
        var method = "GET";
        var path1 = "/api/statements/123";
        var path2 = "/api/statements/456";
        var signature1 = hmacSha256LinkSigner.sign(path1, expires, method);
        var signature2 = hmacSha256LinkSigner.sign(path2, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different expiration times")
    void testSignWithMethod_DifferentExpires() {
        var path = "/api/statements/123";
        var method = "GET";
        var expires1 = 1234567890L;
        var expires2 = 9876543210L;
        var signature1 = hmacSha256LinkSigner.sign(path, expires1, method);
        var signature2 = hmacSha256LinkSigner.sign(path, expires2, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different methods")
    void testSignWithMethod_DifferentMethods() {
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var method1 = "GET";
        var method2 = "POST";
        var signature1 = hmacSha256LinkSigner.sign(path, expires, method1);
        var signature2 = hmacSha256LinkSigner.sign(path, expires, method2);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures with different secret keys")
    void testSignWithMethod_DifferentSecrets() {
        var util1 = new HmacSha256LinkSigner("secret1");
        var util2 = new HmacSha256LinkSigner("secret2");
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var method = "GET";
        var signature1 = util1.sign(path, expires, method);
        var signature2 = util2.sign(path, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should handle empty path")
    void testSignWithMethod_EmptyPath() {
        var path = "";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty method")
    void testSignWithMethod_EmptyMethod() {
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var method = "";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle special characters in path")
    void testSignWithMethod_SpecialCharactersInPath() {
        var path = "/api/statements/file%20name.pdf?param=value&other=123";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle Unicode characters in path")
    void testSignWithMethod_UnicodeCharacters() {
        var path = "/api/statements/文件名.pdf";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle long path strings")
    void testSignWithMethod_LongPath() {
        var longPath = new StringBuilder("/api/statements/");
        for (int i = 0; i < 100; i++) {
            longPath.append("segment").append(i).append("/");
        }
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(longPath.toString(), expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle zero expiration time")
    void testSignWithMethod_ZeroExpires() {
        var path = "/api/statements/123";
        var expires = 0L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle negative expiration time")
    void testSignWithMethod_NegativeExpires() {
        var path = "/api/statements/123";
        var expires = -1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should handle maximum long expiration time")
    void testSignWithMethod_MaxLongExpires() {
        var path = "/api/statements/123";
        var expires = Long.MAX_VALUE;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should generate valid Base64 URL-encoded signature")
    void testSignWithMethod_Base64UrlEncoding() {
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.contains("+"));
        assertFalse(signature.contains("/"));
        assertFalse(signature.endsWith("="));
    }

    @Test
    @DisplayName("Should handle various HTTP methods")
    void testSignWithMethod_VariousHttpMethods() {
        var path = "/api/statements/123";
        var expires = 1234567890L;
        var getSignature = hmacSha256LinkSigner.sign(path, expires, "GET");
        var postSignature = hmacSha256LinkSigner.sign(path, expires, "POST");
        var putSignature = hmacSha256LinkSigner.sign(path, expires, "PUT");
        var deleteSignature = hmacSha256LinkSigner.sign(path, expires, "DELETE");
        var patchSignature = hmacSha256LinkSigner.sign(path, expires, "PATCH");
        assertNotNull(getSignature);
        assertNotNull(postSignature);
        assertNotNull(putSignature);
        assertNotNull(deleteSignature);
        assertNotNull(patchSignature);
        assertNotEquals(getSignature, postSignature);
        assertNotEquals(getSignature, putSignature);
        assertNotEquals(postSignature, deleteSignature);
    }

    @Test
    @DisplayName("Should throw exception when signing with empty secret key")
    void testConstructor_EmptySecret() {
        var util = new HmacSha256LinkSigner("");
        assertNotNull(util);
        assertThrows(SignatureException.class, () -> {
            util.sign("/path", 123L, "GET");
        });
    }

    @Test
    @DisplayName("Should handle pipe character in data components")
    void testSignWithMethod_PipeCharacterHandling() {
        var path = "/api/statements/file|with|pipes.pdf";
        var expires = 1234567890L;
        var method = "GET";
        var signature = hmacSha256LinkSigner.sign(path, expires, method);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should produce signatures of consistent length")
    void testSignWithMethod_ConsistentLength() {
        var path1 = "/short";
        var path2 = "/very/long/path/with/many/segments/and/parameters";
        var expires = 1234567890L;
        var method = "GET";
        var signature1 = hmacSha256LinkSigner.sign(path1, expires, method);
        var signature2 = hmacSha256LinkSigner.sign(path2, expires, method);
        assertNotNull(signature1);
        assertNotNull(signature2);
        assertTrue(signature1.length() > 0);
        assertTrue(signature2.length() > 0);
        assertEquals(signature1.length(), signature2.length());
    }
}
