package com.example.statementservice.service;

import com.example.statementservice.model.entity.SignedLink;
import com.example.statementservice.repository.SignedLinkRepository;
import com.example.statementservice.util.LinkValidationResult;
import com.example.statementservice.util.SignatureUtil;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignedLinkService {

    public static final String EXPIRES_PATH_VARIABLE = "?expires=";
    public static final String SIGNATURE_PATH_VARIABLE = "&signature=";

    private final SignedLinkRepository signedLinkRepository;
    private final SignatureUtil signatureUtil;

    @Value("${statement.files.link-expiry-seconds:900}")
    private long defaultExpirySeconds;

    @Value("${statement.api.download-path:/api/v1/statements/download/}")
    private String downloadPath;

    @Transactional
    public SignedLink createSignedLink(UUID statementId, boolean singleUse, String createdBy, String basePath) {
        var expires = OffsetDateTime.now().plusSeconds(defaultExpirySeconds);
        var link = buildSignedDownloadLink(statementId, singleUse, createdBy, basePath, expires);
        signedLinkRepository.save(link);
        return link;
    }

    private SignedLink buildSignedDownloadLink(
            UUID statementId, boolean singleUse, String createdBy, String basePath, OffsetDateTime expires) {
        var link = new SignedLink();
        link.setId(UUID.randomUUID());
        link.setStatementId(statementId);
        link.setToken(this.signatureUtil.signWithMethod(basePath, expires.toEpochSecond(), HttpMethod.GET.toString()));
        link.setExpiresAt(expires);
        link.setSingleUse(singleUse);
        link.setUsed(false);
        link.setCreatedAt(OffsetDateTime.now());
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

        if (link.getExpiresAt().isBefore(OffsetDateTime.now())) {
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

    private String getServerBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    public String getFilesBaseUrl(String fileName) {
        var filesBaseUrl = getServerBaseUrl();
        var base = filesBaseUrl.endsWith("/") ? filesBaseUrl.substring(0, filesBaseUrl.length() - 1) : filesBaseUrl;
        return URI.create(base + downloadPath + fileName).toString();
    }
}
