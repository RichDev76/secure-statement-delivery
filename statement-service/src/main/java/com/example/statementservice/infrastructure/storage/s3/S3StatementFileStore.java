package com.example.statementservice.infrastructure.storage.s3;

import com.example.statementservice.shared.ContentDigest;
import com.example.statementservice.statement.StatementFileStore;
import com.example.statementservice.statement.StatementStorageUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3StatementFileStore implements StatementFileStore {

    private static final String FILE_EXTENSION_PDF_ENC = ".pdf.enc";
    private static final String STATEMENTS_PREFIX = "statements";
    private static final String KEY_FORMAT = "%s/%s/%d/%02d/%s" + FILE_EXTENSION_PDF_ENC;

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    private final ContentDigest contentDigest;

    @Override
    public String store(UUID id, String accountNumber, LocalDate statementDate, ContentWriter writer)
            throws IOException {
        var accountNumberHash = contentDigest.hexOf(accountNumber.trim().getBytes(StandardCharsets.UTF_8));
        var key = buildKey(accountNumberHash, statementDate, id);

        var buffer = new ByteArrayOutputStream();
        writer.writeTo(buffer);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(buffer.toByteArray()));
        } catch (SdkException e) {
            log.error("Failed to store object - bucket: {}, key: {}", properties.getBucket(), key, e);
            throw new IOException("Failed to store statement in object storage", e);
        }
        return key;
    }

    @Override
    public InputStream open(String reference) throws IOException {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(reference)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException("No object found for the requested reference");
        } catch (SdkException e) {
            // Mirrors exists(): an outage must not be miscategorized downstream - see ADR 0021.
            log.error("Failed to open object - bucket: {}, key: {}", properties.getBucket(), reference, e);
            throw new StatementStorageUnavailableException("Failed to open statement in object storage", e);
        }
    }

    @Override
    public boolean exists(String reference) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(reference)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            // Any other SdkException (network/auth/outage) must not be miscategorized as "file
            // missing" - see ADR 0021.
            log.error("Failed to check object existence - bucket: {}, key: {}", properties.getBucket(), reference, e);
            throw new StatementStorageUnavailableException("Failed to check statement existence in object storage", e);
        }
    }

    private String buildKey(String accountHash, LocalDate statementDate, UUID id) {
        return String.format(
                Locale.ROOT,
                KEY_FORMAT,
                STATEMENTS_PREFIX,
                accountHash,
                statementDate.getYear(),
                statementDate.getMonthValue(),
                id);
    }
}
