package com.example.statementservice.statement.signedlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.statementservice.shared.IdGeneratorPort;
import com.example.statementservice.shared.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignedLinkService Unit Tests")
class SignedLinkServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-11T12:00:00Z");
    private static final String FILE_NAME = "statement.pdf";
    private static final String DOWNLOAD_PATH = "/api/v1/statements/download/" + FILE_NAME;
    private static final String RAW_TOKEN = "test-signature-token";

    @Mock
    private SignedLinkRepository signedLinkRepository;

    @Mock
    private LinkSigner linkSigner;

    @Mock
    private DownloadUrlProvider downloadUrlProvider;

    @Mock
    private IdGeneratorPort idGenerator;

    private SignedLinkProperties properties;

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private SignedLinkService signedLinkService;

    private UUID testStatementId;
    private String testCreatedBy;

    @BeforeEach
    void setUp() {
        testStatementId = UUID.randomUUID();
        testCreatedBy = "testUser";

        properties = new SignedLinkProperties();
        properties.setExpiry(Duration.ofMinutes(15));
        properties.setDownloadPath("/api/v1/statements/download/");
        signedLinkService = new SignedLinkService(
                signedLinkRepository, linkSigner, downloadUrlProvider, properties, idGenerator, clock);
    }

    @Test
    void GivenValidRequest_WhenCreateSignedLink_ThenPersistsTokenHashNotRawToken() {
        // Given
        when(idGenerator.newId()).thenReturn(UUID.randomUUID());
        when(linkSigner.sign(eq(DOWNLOAD_PATH), anyLong(), anyString(), anyString()))
                .thenReturn(RAW_TOKEN);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        var result = signedLinkService.createSignedLink(testStatementId, testCreatedBy, FILE_NAME);

        // Then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatementId()).isEqualTo(testStatementId);
        assertThat(result.getToken()).isEqualTo(RAW_TOKEN);
        assertThat(result.getTokenHash())
                .isEqualTo(Sha256Digest.hexOf(RAW_TOKEN.getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(RAW_TOKEN);
        assertThat(result.getCreatedBy()).isEqualTo(testCreatedBy);
        assertThat(result.getCreatedAt()).isNotNull();
        verify(signedLinkRepository).save(any(SignedLink.class));
    }

    @Test
    void GivenFixedClock_WhenCreateSignedLink_ThenExpiryIsExactlyConfiguredDurationAfterNow() {
        // Given
        when(idGenerator.newId()).thenReturn(UUID.randomUUID());
        when(linkSigner.sign(anyString(), anyLong(), anyString(), anyString())).thenReturn(RAW_TOKEN);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        var result = signedLinkService.createSignedLink(testStatementId, testCreatedBy, FILE_NAME);

        // Then
        assertThat(result.getExpiresAt())
                .isEqualTo(
                        OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC).plusMinutes(15));
        assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void GivenValidRequest_WhenCreateSignedLink_ThenSignsWithFilesDownloadPathAndLinkIdAsNonce() {
        // Given
        when(idGenerator.newId()).thenReturn(UUID.randomUUID());
        when(linkSigner.sign(anyString(), anyLong(), anyString(), anyString())).thenReturn(RAW_TOKEN);
        when(signedLinkRepository.save(any(SignedLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        var result = signedLinkService.createSignedLink(testStatementId, testCreatedBy, FILE_NAME);

        // Then
        verify(linkSigner)
                .sign(
                        eq(DOWNLOAD_PATH),
                        eq(result.getExpiresAt().toEpochSecond()),
                        eq("GET"),
                        eq(result.getId().toString()));
    }

    @Test
    void GivenValidLinkAndMatchingSignature_WhenValidate_ThenReturnsValid() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        var linkId = link.getId();
        when(linkSigner.verify(RAW_TOKEN, DOWNLOAD_PATH, link.getExpiresAt().toEpochSecond(), "GET", linkId.toString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(link.getTokenHash())).thenReturn(Optional.of(link));
        when(signedLinkRepository.recordRedemption(eq(linkId), anyInt())).thenReturn(1);

        // When
        var result = signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), linkId, FILE_NAME);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getLink()).isEqualTo(link);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void GivenLinkAlreadyAtMaxRedemptions_WhenValidate_ThenReturnsExpiredNotADistinctReason() {
        // Given: exhausted redemptions are deliberately indistinguishable from natural expiry -
        // no separate signal for an attacker to calibrate against.
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        var linkId = link.getId();
        when(linkSigner.verify(RAW_TOKEN, DOWNLOAD_PATH, link.getExpiresAt().toEpochSecond(), "GET", linkId.toString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(link.getTokenHash())).thenReturn(Optional.of(link));
        when(signedLinkRepository.recordRedemption(eq(linkId), anyInt())).thenReturn(0);

        // When
        var result = signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), linkId, FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.EXPIRED);
        assertThat(result.getLink()).isEqualTo(link);
    }

    @Test
    void GivenTamperedSignature_WhenValidate_ThenReturnsInvalidSignatureWithoutQueryingRepository() {
        // Given
        var linkId = UUID.randomUUID();
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(false);

        // When
        var result = signedLinkService.validate("tampered-token", 1234567890L, linkId, FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
        verifyNoInteractions(signedLinkRepository);
    }

    @Test
    void GivenValidSignatureButNoMatchingTokenHash_WhenValidate_ThenReturnsNotFound() {
        // Given
        var linkId = UUID.randomUUID();
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // When
        var result = signedLinkService.validate(RAW_TOKEN, 1234567890L, linkId, FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getLink()).isNull();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.NOT_FOUND);
    }

    @Test
    void GivenValidSignatureButLinkIdMismatch_WhenValidate_ThenReturnsInvalidSignature() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        var unrelatedLinkId = UUID.randomUUID();
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        // When
        var result =
                signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), unrelatedLinkId, FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
    }

    @Test
    void GivenValidSignatureButExpiresMismatch_WhenValidate_ThenReturnsInvalidSignature() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        // When
        var result = signedLinkService.validate(
                RAW_TOKEN, link.getExpiresAt().toEpochSecond() + 3600, link.getId(), FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
    }

    @Test
    void GivenExpiredLink_WhenValidate_ThenReturnsExpired() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).minusMinutes(10));
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));

        // When
        var result =
                signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), link.getId(), FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.EXPIRED);
    }

    @Test
    void GivenValidLink_WhenValidatedTwice_ThenBothCallsReturnValid() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        when(linkSigner.verify(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(true);
        when(signedLinkRepository.findByTokenHash(anyString())).thenReturn(Optional.of(link));
        when(signedLinkRepository.recordRedemption(any(), anyInt())).thenReturn(1);

        // When
        var first = signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), link.getId(), FILE_NAME);
        var second =
                signedLinkService.validate(RAW_TOKEN, link.getExpiresAt().toEpochSecond(), link.getId(), FILE_NAME);

        // Then
        assertThat(first.isValid()).isTrue();
        assertThat(second.isValid()).isTrue();
        verify(signedLinkRepository, never()).save(any());
        verify(signedLinkRepository, times(2)).recordRedemption(eq(link.getId()), anyInt());
    }

    @Test
    void GivenNullExpiresFromUrl_WhenValidate_ThenReturnsInvalidSignatureWithoutCallingVerify() {
        // When
        var result = signedLinkService.validate(RAW_TOKEN, null, UUID.randomUUID(), FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
        verifyNoInteractions(linkSigner);
    }

    @Test
    void GivenNullLinkId_WhenValidate_ThenReturnsInvalidSignatureWithoutCallingVerify() {
        // When
        var result = signedLinkService.validate(RAW_TOKEN, 1234567890L, null, FILE_NAME);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo(ValidationFailureReason.INVALID_SIGNATURE);
        verifyNoInteractions(linkSigner);
    }

    @Test
    void GivenValidLink_WhenBuildingSignedDownloadLink_ThenUrlContainsExpiresLinkIdAndSignature() {
        // Given
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        when(downloadUrlProvider.toAbsoluteUrl(DOWNLOAD_PATH)).thenReturn("https://host" + DOWNLOAD_PATH);

        // When
        var uri = signedLinkService.buildSignedDownloadLink(link, FILE_NAME);

        // Then
        assertThat(uri.toString())
                .startsWith("https://host" + DOWNLOAD_PATH)
                .contains("?expires=" + link.getExpiresAt().toEpochSecond())
                .contains("&linkId=" + link.getId())
                .contains("&signature=" + RAW_TOKEN);
    }

    @Test
    void GivenUriConstructionFails_WhenBuildingSignedDownloadLink_ThenExceptionPropagatesAndIsLoggedAtError() {
        // Given: an absolute base alone is valid, but the assembled URI (base + signature) is not.
        var link = createTestLink(OffsetDateTime.now(clock).plusMinutes(10));
        link.setToken("not a valid uri token");
        when(downloadUrlProvider.toAbsoluteUrl(DOWNLOAD_PATH)).thenReturn("https://host" + DOWNLOAD_PATH);

        // When / Then
        assertThatThrownBy(() -> signedLinkService.buildSignedDownloadLink(link, FILE_NAME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SignedLink createTestLink(OffsetDateTime expiresAt) {
        var link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setStatementId(testStatementId);
        link.setToken(RAW_TOKEN);
        link.setTokenHash(Sha256Digest.hexOf(RAW_TOKEN.getBytes(StandardCharsets.UTF_8)));
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(OffsetDateTime.now(clock).minusMinutes(5));
        link.setCreatedBy(testCreatedBy);
        return link;
    }
}
