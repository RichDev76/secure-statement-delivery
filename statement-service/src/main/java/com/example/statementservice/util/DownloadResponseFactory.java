package com.example.statementservice.util;

import com.example.statementservice.exception.DecryptionFailedException;
import com.example.statementservice.exception.DownloadFileMissingException;
import com.example.statementservice.exception.DownloadInvalidSignatureException;
import com.example.statementservice.exception.DownloadLinkExpiredException;
import com.example.statementservice.exception.StatementNotFoundException;
import com.example.statementservice.service.DownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DownloadResponseFactory {

    public static final String CACHE_CONTROL = "no-store, no-cache, must-revalidate";
    public static final String HTTP_HEADER_PRAGMA = "Pragma";
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";
    public static final String PRAGMA_NO_CACHE = "no-cache";

    public ResponseEntity<Resource> build(String fileName, DownloadService.DownloadStreamResult result) {
        switch (result.outcome()) {
            case OK -> {
                var resource = new InputStreamResource(result.stream().get());
                var headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData(CONTENT_DISPOSITION_ATTACHMENT, fileName);
                headers.setCacheControl(CACHE_CONTROL);
                headers.add(HTTP_HEADER_PRAGMA, PRAGMA_NO_CACHE);
                return ResponseEntity.ok().headers(headers).body(resource);
            }
            case INVALID_SIGNATURE -> {
                log.warn("Invalid signature for download - fileName: {}", fileName);
                throw new DownloadInvalidSignatureException(
                        "The download link signature is invalid or has been tampered with.");
            }
            case LINK_EXPIRED_OR_USED -> {
                log.warn("Download link expired or used - fileName: {}", fileName);
                throw new DownloadLinkExpiredException("The download link has expired or has already been used.");
            }
            case STATEMENT_NOT_FOUND -> {
                log.warn("Statement not found - fileName: {}", fileName);
                throw new StatementNotFoundException("The requested statement could not be found.");
            }
            case FILE_MISSING -> {
                log.warn("File missing - fileName: {}", fileName);
                throw new DownloadFileMissingException("The statement file is missing from storage.");
            }
            case DECRYPTION_FAILED -> {
                log.error("Decryption failed during download for fileName: {}", fileName);
                throw new DecryptionFailedException("Failed to decrypt the statement file.");
            }
            default -> {
                throw new DownloadInvalidSignatureException("Access to the requested resource is denied.");
            }
        }
    }
}
