package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignedLinkService Unit Tests")
class SignedLinkServiceTest {

    @Mock
    private SignedLinkRepository signedLinkRepository;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private LinkSigner linkSigner;

    @Mock
    private DownloadUrlProvider downloadUrlProvider;

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @InjectMocks
    private SignedLinkService signedLinkService;

    private UUID testStatementId;
    private String testToken;
    private String testCreatedBy;
    private String testBasePath;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testToken = "test-signature-token";
        testCreatedBy = "testUser";
        testBasePath = "/api/v1/statements/download/test.pdf";

        ReflectionTestUtils.setField(signedLinkService, "defaultExpirySeconds", 900L);
    }

    @Test
    @DisplayName("createSignedLink - should create and save single-use link")
    void createSignedLink_SingleUse() {

        when(linkSigner.sign(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testStatementId);
        assertThat(result.getToken()).isEqualTo(testToken);
        assertThat(result.isSingleUse()).isTrue();
        assertThat(result.isUsed()).isFalse();
        assertThat(result.getCreatedBy()).isEqualTo(testCreatedBy);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(result.getCreatedAt());
        verify(linkSigner).sign(eq(testBasePath), anyLong(), eq("GET"));
        verify(signedLinkRepository).save(any(SignedLink.class));
    }

    @Test
    @DisplayName("createSignedLink - should create multi-use link")
    void createSignedLink_MultiUse() {

        when(linkSigner.sign(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = signedLinkService.createSignedLink(testStatementId, false, testCreatedBy, testBasePath);
        assertThat(result).isNotNull();
        assertThat(result.isSingleUse()).isFalse();
        assertThat(result.isUsed()).isFalse();
        verify(signedLinkRepository).save(any(SignedLink.class));
    }

    @Test
    @DisplayName("createSignedLink - should expire exactly link-expiry-seconds after the clock instant")
    void GivenFixedClock_WhenCreatingSignedLink_ThenExpiryIsExactlyDefaultExpiryAfterNow() {

        when(linkSigner.sign(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);
        assertThat(result.getExpiresAt())
                .isEqualTo(
                        OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC).plusSeconds(900));
        assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("createSignedLink - should generate signature with correct parameters")
    void createSignedLink_SignatureParameters() {

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expiresCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        when(linkSigner.sign(anyString(), anyLong(), anyString())).thenReturn(testToken);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        signedLinkService.createSignedLink(testStatementId, true, testCreatedBy, testBasePath);
        verify(linkSigner).sign(pathCaptor.capture(), expiresCaptor.capture(), methodCaptor.capture());
        assertThat(pathCaptor.getValue()).isEqualTo(testBasePath);
        assertThat(expiresCaptor.getValue()).isGreaterThan(0);
        assertThat(methodCaptor.getValue()).isEqualTo("GET");
    }

    @Test
    @DisplayName("validateAndConsume - should return valid result for valid single-use link")
    void validateAndConsume_ValidSingleUse() {

        var link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        when(signedLinkRepository.consumeSingleUse(testToken)).thenAnswer(invocation -> 1);
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNull();
        verify(signedLinkRepository).findByToken(testToken);
    }

    @Test
    @DisplayName("validateAndConsume - should return valid result for valid multi-use link without marking as used")
    void validateAndConsume_ValidMultiUse() {

        var link = createTestLink(false, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isTrue();
        assertThat(result.getLink()).isEqualTo(link);
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return not found for non-existent token")
    void validateAndConsume_NotFound() {

        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.empty());
        var result = signedLinkService.validateAndConsume(testToken, 1234567890L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isNull();
        assertThat(result.getFailureReason()).isNotNull();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return used result for already used link")
    void validateAndConsume_AlreadyUsed() {

        var link = createTestLink(true, true, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNotNull();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return expired result for expired link")
    void validateAndConsume_Expired() {

        var link = createTestLink(true, false, OffsetDateTime.now().minusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNotNull();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should handle link expiring exactly now")
    void validateAndConsume_ExpiringNow() {

        var link = createTestLink(true, false, OffsetDateTime.now().minusSeconds(1));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isFalse();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should mark only single-use links as used")
    void validateAndConsume_OnlySingleUseMarked() {

        var multiUseLink = createTestLink(false, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(multiUseLink));
        var result = signedLinkService.validateAndConsume(
                testToken, multiUseLink.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isTrue();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should not mark expired single-use link as used")
    void validateAndConsume_ExpiredNotMarkedUsed() {

        var link = createTestLink(true, false, OffsetDateTime.now().minusHours(1));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond());
        assertThat(result.isValid()).isFalse();
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should use pessimistic locking via findByTokenForUpdate")
    void validateAndConsume_UsesPessimisticLocking() {

        var link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        when(signedLinkRepository.consumeSingleUse(testToken)).thenReturn(1);
        signedLinkService.validateAndConsume(testToken, link.getExpiresAt().toEpochSecond());
        verify(signedLinkRepository).findByToken(testToken);
    }

    @Test
    @DisplayName("validateAndConsume - should return invalid signature when expires mismatch")
    void validateAndConsume_ExpiresMismatch() {

        var link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(
                testToken, link.getExpiresAt().toEpochSecond() + 3600);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
        verify(signedLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateAndConsume - should return invalid signature when expires is null")
    void validateAndConsume_ExpiresNull() {

        var link = createTestLink(true, false, OffsetDateTime.now().plusMinutes(10));
        when(signedLinkRepository.findByToken(testToken)).thenReturn(Optional.of(link));
        var result = signedLinkService.validateAndConsume(testToken, null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
    }

    private SignedLink createTestLink(boolean singleUse, boolean used, OffsetDateTime expiresAt) {

        var link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setStatementId(testStatementId);
        link.setToken(testToken);
        link.setSingleUse(singleUse);
        link.setUsed(used);
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        link.setCreatedBy(testCreatedBy);
        return link;
    }
}
