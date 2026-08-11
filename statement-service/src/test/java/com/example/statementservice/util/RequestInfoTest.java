package com.example.statementservice.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestInfo Tests")
class RequestInfoTest {

    @Test
    @DisplayName("Should create RequestInfo with no-arg constructor")
    void testNoArgConstructor() {
        var requestInfo = new RequestInfo();
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should create RequestInfo with all-args constructor")
    void testAllArgsConstructor() {
        var clientIp = "192.168.1.100";
        var userAgent = "Mozilla/5.0";
        var performedBy = "john.doe";
        var requestInfo = new RequestInfo(clientIp, userAgent, performedBy);
        assertNotNull(requestInfo);
        assertEquals(clientIp, requestInfo.getClientIp());
        assertEquals(userAgent, requestInfo.getUserAgent());
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should set and get clientIp")
    void testSetAndGetClientIp() {
        var requestInfo = new RequestInfo();
        var clientIp = "10.0.0.5";
        requestInfo.setClientIp(clientIp);
        assertEquals(clientIp, requestInfo.getClientIp());
    }

    @Test
    @DisplayName("Should set and get userAgent")
    void testSetAndGetUserAgent() {
        var requestInfo = new RequestInfo();
        var userAgent = "Chrome/90.0.4430.93";
        requestInfo.setUserAgent(userAgent);
        assertEquals(userAgent, requestInfo.getUserAgent());
    }

    @Test
    @DisplayName("Should set and get performedBy")
    void testSetAndGetPerformedBy() {
        var requestInfo = new RequestInfo();
        var performedBy = "admin";
        requestInfo.setPerformedBy(performedBy);
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle null values in all-args constructor")
    void testAllArgsConstructor_NullValues() {
        var requestInfo = new RequestInfo(null, null, null);
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should allow setting null values via setters")
    void testSetters_NullValues() {
        var requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");
        requestInfo.setClientIp(null);
        requestInfo.setUserAgent(null);
        requestInfo.setPerformedBy(null);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should allow updating values via setters")
    void testUpdateValues() {
        var requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");
        requestInfo.setClientIp("10.0.0.1");
        requestInfo.setUserAgent("Chrome");
        requestInfo.setPerformedBy("admin");
        assertEquals("10.0.0.1", requestInfo.getClientIp());
        assertEquals("Chrome", requestInfo.getUserAgent());
        assertEquals("admin", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle empty strings")
    void testEmptyStrings() {
        var requestInfo = new RequestInfo("", "", "");
        assertNotNull(requestInfo);
        assertEquals("", requestInfo.getClientIp());
        assertEquals("", requestInfo.getUserAgent());
        assertEquals("", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle special characters in fields")
    void testSpecialCharacters() {
        var clientIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"; // IPv6
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        var performedBy = "user@example.com";
        var requestInfo = new RequestInfo(clientIp, userAgent, performedBy);
        assertEquals(clientIp, requestInfo.getClientIp());
        assertEquals(userAgent, requestInfo.getUserAgent());
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle long strings")
    void testLongStrings() {
        var longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59";
        var requestInfo = new RequestInfo();
        requestInfo.setUserAgent(longUserAgent);
        assertEquals(longUserAgent, requestInfo.getUserAgent());
    }

    @Test
    @DisplayName("Should maintain independence between instances")
    void testInstanceIndependence() {
        var info1 = new RequestInfo("192.168.1.1", "Firefox", "user1");
        var info2 = new RequestInfo("192.168.1.2", "Chrome", "user2");
        info1.setClientIp("10.0.0.1");
        assertEquals("10.0.0.1", info1.getClientIp());
        assertEquals("192.168.1.2", info2.getClientIp());
        assertNotEquals(info1.getClientIp(), info2.getClientIp());
    }

    @Test
    @DisplayName("Should create multiple instances with same values")
    void testMultipleInstancesSameValues() {
        var clientIp = "192.168.1.100";
        var userAgent = "Safari";
        var performedBy = "testuser";
        var info1 = new RequestInfo(clientIp, userAgent, performedBy);
        var info2 = new RequestInfo(clientIp, userAgent, performedBy);
        assertNotSame(info1, info2);
        assertEquals(info1.getClientIp(), info2.getClientIp());
        assertEquals(info1.getUserAgent(), info2.getUserAgent());
        assertEquals(info1.getPerformedBy(), info2.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle system default values")
    void testSystemDefaultValues() {
        var requestInfo = new RequestInfo("unknown", "unknown", "system");
        assertEquals("unknown", requestInfo.getClientIp());
        assertEquals("unknown", requestInfo.getUserAgent());
        assertEquals("system", requestInfo.getPerformedBy());
    }

    @Test
    @DisplayName("Should handle localhost scenarios")
    void testLocalhostScenarios() {
        var requestInfo = new RequestInfo("127.0.0.1", "PostmanRuntime/7.28.0", "developer");
        assertEquals("127.0.0.1", requestInfo.getClientIp());
        assertEquals("PostmanRuntime/7.28.0", requestInfo.getUserAgent());
        assertEquals("developer", requestInfo.getPerformedBy());
    }
}
