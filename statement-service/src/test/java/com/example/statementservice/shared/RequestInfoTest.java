package com.example.statementservice.shared;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestInfo Tests")
class RequestInfoTest {

    @Test
    void GivenAllArguments_WhenCreatingRequestInfo_ThenAllFieldsAreAssigned() {
        var clientIp = "192.168.1.100";
        var userAgent = "Mozilla/5.0";
        var performedBy = "john.doe";
        var requestInfo = new RequestInfo(clientIp, userAgent, performedBy);
        assertEquals(clientIp, requestInfo.clientIp());
        assertEquals(userAgent, requestInfo.userAgent());
        assertEquals(performedBy, requestInfo.performedBy());
    }

    @Test
    void GivenNullArguments_WhenCreatingRequestInfo_ThenNullsAreAccepted() {
        var requestInfo = new RequestInfo(null, null, null);
        assertNull(requestInfo.clientIp());
        assertNull(requestInfo.userAgent());
        assertNull(requestInfo.performedBy());
    }

    @Test
    void GivenEmptyStrings_WhenCreatingRequestInfo_ThenEmptyValuesAreStored() {
        var requestInfo = new RequestInfo("", "", "");
        assertEquals("", requestInfo.clientIp());
        assertEquals("", requestInfo.userAgent());
        assertEquals("", requestInfo.performedBy());
    }

    @Test
    void GivenSpecialCharacterValues_WhenCreatingRequestInfo_ThenValuesAreStoredUnchanged() {
        var clientIp = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"; // IPv6
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        var performedBy = "user@example.com";
        var requestInfo = new RequestInfo(clientIp, userAgent, performedBy);
        assertEquals(clientIp, requestInfo.clientIp());
        assertEquals(userAgent, requestInfo.userAgent());
        assertEquals(performedBy, requestInfo.performedBy());
    }

    @Test
    void GivenLongUserAgent_WhenCreatingRequestInfo_ThenValueIsStoredUnchanged() {
        var longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59";
        var requestInfo = new RequestInfo("127.0.0.1", longUserAgent, "user");
        assertEquals(longUserAgent, requestInfo.userAgent());
    }

    @Test
    void GivenSameFieldValues_WhenCreatingTwoInstances_ThenTheyAreEqual() {
        var clientIp = "192.168.1.100";
        var userAgent = "Safari";
        var performedBy = "testuser";
        var info1 = new RequestInfo(clientIp, userAgent, performedBy);
        var info2 = new RequestInfo(clientIp, userAgent, performedBy);
        assertNotSame(info1, info2);
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void GivenDifferentFieldValues_WhenCreatingTwoInstances_ThenTheyAreNotEqual() {
        var info1 = new RequestInfo("192.168.1.1", "Firefox", "user1");
        var info2 = new RequestInfo("192.168.1.2", "Chrome", "user2");
        assertNotEquals(info1, info2);
    }

    @Test
    void GivenSystemStyleValues_WhenCreatingRequestInfo_ThenValuesAreStored() {
        var requestInfo = new RequestInfo("unknown", "unknown", "system");
        assertEquals("unknown", requestInfo.clientIp());
        assertEquals("unknown", requestInfo.userAgent());
        assertEquals("system", requestInfo.performedBy());
    }

    @Test
    void GivenLocalhostAddresses_WhenCreatingRequestInfo_ThenValuesAreStored() {
        var requestInfo = new RequestInfo("127.0.0.1", "PostmanRuntime/7.28.0", "developer");
        assertEquals("127.0.0.1", requestInfo.clientIp());
        assertEquals("PostmanRuntime/7.28.0", requestInfo.userAgent());
        assertEquals("developer", requestInfo.performedBy());
    }
}
