package com.example.statementservice.statement.download.infrastructure;

import com.example.statementservice.statement.StatementNotFoundException;
import com.example.statementservice.statement.download.DecryptionFailedException;
import com.example.statementservice.statement.download.DownloadFileMissingException;
import com.example.statementservice.statement.download.DownloadInvalidSignatureException;
import com.example.statementservice.statement.download.DownloadLinkExpiredException;
import com.example.statementservice.statement.download.DownloadRateLimitedException;
import com.example.statementservice.statement.download.DownloadService;
import com.example.statementservice.statement.download.DownloadStorageUnavailableException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DownloadResponseFactory {

    public static final String CACHE_CONTROL = "no-store, no-cache, must-revalidate";
    public static final String HTTP_HEADER_PRAGMA = "Pragma";
    public static final String HTTP_HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";
    public static final String PRAGMA_NO_CACHE = "no-cache";
    public static final String REFERRER_POLICY_NO_REFERRER = "no-referrer";

    // Outcomes are already logged in DownloadService keyed by statementId/linkId - don't re-log fileName here.
    public ResponseEntity<Resource> build(String fileName, DownloadService.DownloadStreamResult result) {
        switch (result.outcome()) {
            case OK -> {
                var resource = new InputStreamResource(result.stream().get());
                var headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData(CONTENT_DISPOSITION_ATTACHMENT, fileName);
                headers.setCacheControl(CACHE_CONTROL);
                headers.add(HTTP_HEADER_PRAGMA, PRAGMA_NO_CACHE);
                headers.add(HTTP_HEADER_REFERRER_POLICY, REFERRER_POLICY_NO_REFERRER);
                return ResponseEntity.ok().headers(headers).body(resource);
            }
            case INVALID_SIGNATURE ->
                throw new DownloadInvalidSignatureException(
                        "The download link signature is invalid or has been tampered with.");
            case LINK_EXPIRED -> throw new DownloadLinkExpiredException("The download link has expired.");
            case STATEMENT_NOT_FOUND ->
                throw new StatementNotFoundException("The requested statement could not be found.");
            case FILE_MISSING -> throw new DownloadFileMissingException("The statement file is missing from storage.");
            case DECRYPTION_FAILED -> throw new DecryptionFailedException("Failed to decrypt the statement file.");
            case RATE_LIMITED ->
                throw new DownloadRateLimitedException("Too many requests for this link. Please try again later.");
            case STORAGE_UNAVAILABLE ->
                throw new DownloadStorageUnavailableException(
                        "The statement storage backend is temporarily unavailable. Please try again shortly.");
            default -> throw new DownloadInvalidSignatureException("Access to the requested resource is denied.");
        }
    }
}
