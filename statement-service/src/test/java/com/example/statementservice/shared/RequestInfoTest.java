package com.example.statementservice.shared;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestInfo Tests")
class RequestInfoTest {

    @Test
    void GivenNoArgConstructor_WhenCreatingRequestInfo_ThenAllFieldsAreNull() {
        var requestInfo = new RequestInfo();
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    void GivenAllArguments_WhenCreatingRequestInfo_ThenAllFieldsAreAssigned() {
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
    void GivenRequestInfo_WhenSettingClientIp_ThenGetterReturnsIt() {
        var requestInfo = new RequestInfo();
        var clientIp = "10.0.0.5";
        requestInfo.setClientIp(clientIp);
        assertEquals(clientIp, requestInfo.getClientIp());
    }

    @Test
    void GivenRequestInfo_WhenSettingUserAgent_ThenGetterReturnsIt() {
        var requestInfo = new RequestInfo();
        var userAgent = "Chrome/90.0.4430.93";
        requestInfo.setUserAgent(userAgent);
        assertEquals(userAgent, requestInfo.getUserAgent());
    }

    @Test
    void GivenRequestInfo_WhenSettingPerformedBy_ThenGetterReturnsIt() {
        var requestInfo = new RequestInfo();
        var performedBy = "admin";
        requestInfo.setPerformedBy(performedBy);
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    void GivenNullArguments_WhenCreatingRequestInfo_ThenNullsAreAccepted() {
        var requestInfo = new RequestInfo(null, null, null);
        assertNotNull(requestInfo);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    void GivenPopulatedRequestInfo_WhenSettingNulls_ThenNullsAreStored() {
        var requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");
        requestInfo.setClientIp(null);
        requestInfo.setUserAgent(null);
        requestInfo.setPerformedBy(null);
        assertNull(requestInfo.getClientIp());
        assertNull(requestInfo.getUserAgent());
        assertNull(requestInfo.getPerformedBy());
    }

    @Test
    void GivenPopulatedRequestInfo_WhenSettingNewValues_ThenValuesAreReplaced() {
        var requestInfo = new RequestInfo("192.168.1.1", "Firefox", "user");
        requestInfo.setClientIp("10.0.0.1");
        requestInfo.setUserAgent("Chrome");
        requestInfo.setPerformedBy("admin");
        assertEquals("10.0.0.1", requestInfo.getClientIp());
        assertEquals("Chrome", requestInfo.getUserAgent());
        assertEquals("admin", requestInfo.getPerformedBy());
    }

    @Test
    void GivenEmptyStrings_WhenCreatingRequestInfo_ThenEmptyValuesAreStored() {
        var requestInfo = new RequestInfo("", "", "");
        assertNotNull(requestInfo);
        assertEquals("", requestInfo.getClientIp());
        assertEquals("", requestInfo.getUserAgent());
        assertEquals("", requestInfo.getPerformedBy());
    }

    @Test
    void GivenSpecialCharacterValues_WhenCreatingRequestInfo_ThenValuesAreStoredUnchanged() {
        var clientIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"; // IPv6
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        var performedBy = "user@example.com";
        var requestInfo = new RequestInfo(clientIp, userAgent, performedBy);
        assertEquals(clientIp, requestInfo.getClientIp());
        assertEquals(userAgent, requestInfo.getUserAgent());
        assertEquals(performedBy, requestInfo.getPerformedBy());
    }

    @Test
    void GivenLongValues_WhenCreatingRequestInfo_ThenValuesAreStoredUnchanged() {
        var longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59";
        var requestInfo = new RequestInfo();
        requestInfo.setUserAgent(longUserAgent);
        assertEquals(longUserAgent, requestInfo.getUserAgent());
    }

    @Test
    void GivenTwoInstances_WhenMutatingOne_ThenOtherIsUnaffected() {
        var info1 = new RequestInfo("192.168.1.1", "Firefox", "user1");
        var info2 = new RequestInfo("192.168.1.2", "Chrome", "user2");
        info1.setClientIp("10.0.0.1");
        assertEquals("10.0.0.1", info1.getClientIp());
        assertEquals("192.168.1.2", info2.getClientIp());
        assertNotEquals(info1.getClientIp(), info2.getClientIp());
    }

    @Test
    void GivenSameValues_WhenCreatingMultipleInstances_ThenEachHoldsTheValues() {
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
    void GivenSystemStyleValues_WhenCreatingRequestInfo_ThenValuesAreStored() {
        var requestInfo = new RequestInfo("unknown", "unknown", "system");
        assertEquals("unknown", requestInfo.getClientIp());
        assertEquals("unknown", requestInfo.getUserAgent());
        assertEquals("system", requestInfo.getPerformedBy());
    }

    @Test
    void GivenLocalhostAddresses_WhenCreatingRequestInfo_ThenValuesAreStored() {
        var requestInfo = new RequestInfo("127.0.0.1", "PostmanRuntime/7.28.0", "developer");
        assertEquals("127.0.0.1", requestInfo.getClientIp());
        assertEquals("PostmanRuntime/7.28.0", requestInfo.getUserAgent());
        assertEquals("developer", requestInfo.getPerformedBy());
    }
}
