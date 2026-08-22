package com.example.statementservice.statement.signedlink;

import com.example.statementservice.shared.ContentDigest;
import com.example.statementservice.shared.IdGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignedLinkService {

    public static final String EXPIRES_PATH_VARIABLE = "?expires=";
    public static final String LINK_ID_PATH_VARIABLE = "&linkId=";
    public static final String SIGNATURE_PATH_VARIABLE = "&signature=";
    private static final String HTTP_METHOD = HttpMethod.GET.toString();

    private final SignedLinkRepository signedLinkRepository;
    private final LinkSigner linkSigner;
    private final DownloadUrlProvider downloadUrlProvider;
    private final SignedLinkProperties properties;
    private final IdGenerator idGenerator;
    private final ContentDigest contentDigest;
    private final Clock clock;

    @Transactional
    public SignedLink createSignedLink(UUID statementId, String createdBy, String fileName) {
        var id = idGenerator.newId();
        var expires = OffsetDateTime.now(clock).plus(properties.getExpiry());
        var path = getFilesDownloadPath(fileName);
        var rawToken = linkSigner.sign(path, expires.toEpochSecond(), HTTP_METHOD, id.toString());

        var link = new SignedLink();
        link.setId(id);
        link.setStatementId(statementId);
        link.setToken(rawToken);
        link.setTokenHash(contentDigest.hexOf(rawToken.getBytes(StandardCharsets.UTF_8)));
        link.setExpiresAt(expires);
        link.setCreatedAt(OffsetDateTime.now(clock));
        link.setCreatedBy(createdBy);

        signedLinkRepository.save(link);
        return link;
    }

    public URI buildSignedDownloadLink(SignedLink signedLink, String fileName) {
        var absoluteBase = downloadUrlProvider.toAbsoluteUrl(getFilesDownloadPath(fileName));
        var url = absoluteBase
                + EXPIRES_PATH_VARIABLE
                + signedLink.getExpiresAt().toEpochSecond()
                + LINK_ID_PATH_VARIABLE
                + signedLink.getId()
                + SIGNATURE_PATH_VARIABLE
                + signedLink.getToken();
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            log.error("Failed to build signed download link for statementId={}", signedLink.getStatementId(), e);
            throw e;
        }
    }

    @Transactional
    public LinkValidationResult validate(String token, Long expiresFromUrl, UUID linkId, String fileName) {
        if (expiresFromUrl == null || linkId == null) {
            return LinkValidationResult.invalidSignature(null);
        }

        var path = getFilesDownloadPath(fileName);
        if (!linkSigner.verify(token, path, expiresFromUrl, HTTP_METHOD, linkId.toString())) {
            return LinkValidationResult.invalidSignature(null);
        }

        var tokenHash = contentDigest.hexOf(token.getBytes(StandardCharsets.UTF_8));
        var optionalLink = signedLinkRepository.findByTokenHash(tokenHash);
        if (optionalLink.isEmpty()) {
            return LinkValidationResult.notFound();
        }

        var link = optionalLink.get();
        if (!link.getId().equals(linkId) || link.getExpiresAt().toEpochSecond() != expiresFromUrl) {
            log.warn("Link cross-check failed - linkId: {}, statementId: {}", linkId, link.getStatementId());
            return LinkValidationResult.invalidSignature(link);
        }

        if (link.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            log.info("Link expired at: {}", link.getExpiresAt());
            return LinkValidationResult.expired(link);
        }

        // Bounded redemption count: absorbs legitimate retries while capping what a leaked link
        // is worth in total. Exhausted redemptions are deliberately indistinguishable from a
        // naturally expired link - no separate signal for an attacker to calibrate against.
        var redeemed = signedLinkRepository.recordRedemption(link.getId(), properties.getMaxRedemptions());
        if (redeemed == 0) {
            log.info("Link redemption limit reached: {}", link.getId());
            return LinkValidationResult.expired(link);
        }

        return LinkValidationResult.valid(link);
    }

    public String getFilesDownloadPath(String fileName) {
        return properties.getDownloadPath() + fileName;
    }
}
