package com.example.statementservice.statement.signedlink;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignedLinkService {

    public static final String EXPIRES_PATH_VARIABLE = "?expires=";
    public static final String SIGNATURE_PATH_VARIABLE = "&signature=";

    private final SignedLinkRepository signedLinkRepository;
    private final LinkSigner linkSigner;
    private final DownloadUrlProvider downloadUrlProvider;
    private final Clock clock;

    @Value("${statement.files.link-expiry-seconds:900}")
    private long defaultExpirySeconds;

    @Transactional
    public SignedLink createSignedLink(UUID statementId, boolean singleUse, String createdBy, String basePath) {
        var expires = OffsetDateTime.now(clock).plusSeconds(defaultExpirySeconds);
        var link = buildSignedDownloadLink(statementId, singleUse, createdBy, basePath, expires);
        signedLinkRepository.save(link);
        return link;
    }

    private SignedLink buildSignedDownloadLink(
            UUID statementId, boolean singleUse, String createdBy, String basePath, OffsetDateTime expires) {
        var link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setStatementId(statementId);
        link.setToken(linkSigner.sign(basePath, expires.toEpochSecond(), HttpMethod.GET.toString()));
        link.setExpiresAt(expires);
        link.setSingleUse(singleUse);
        link.setUsed(false);
        link.setCreatedAt(OffsetDateTime.now(clock));
        link.setCreatedBy(createdBy);
        return link;
    }

    @Transactional
    public URI buildSignedDownloadLink(SignedLink signedLink, String basePath) {
        var expires = signedLink.getExpiresAt().toEpochSecond();
        try {
            var signature = signedLink.getToken();
            var url = basePath + EXPIRES_PATH_VARIABLE + expires + SIGNATURE_PATH_VARIABLE + signature;
            return URI.create(url);
        } catch (Exception e) {
            return URI.create(basePath);
        }
    }

    @Transactional
    public LinkValidationResult validateAndConsume(String token, Long expiresFromUrl) {
        var optionalSignedLink = signedLinkRepository.findByToken(token);

        if (optionalSignedLink.isEmpty()) {
            return LinkValidationResult.notFound();
        }

        var link = optionalSignedLink.get();

        if (expiresFromUrl == null || link.getExpiresAt().toEpochSecond() != expiresFromUrl) {
            log.warn(
                    "Expires mismatch - URL: {}, stored: {}",
                    expiresFromUrl,
                    link.getExpiresAt().toEpochSecond());
            return LinkValidationResult.invalidSignature(link);
        }

        if (link.isUsed()) {
            return LinkValidationResult.used(link);
        }

        if (link.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            log.info("Link expired at: {}", link.getExpiresAt());
            return LinkValidationResult.expired(link);
        }

        if (link.isSingleUse()) {
            int updated = signedLinkRepository.consumeSingleUse(token);
            if (updated == 0) {
                return LinkValidationResult.used(link);
            }
        }

        return LinkValidationResult.valid(link);
    }

    public String getFilesBaseUrl(String fileName) {
        return downloadUrlProvider.downloadBaseUrl(fileName);
    }
}
