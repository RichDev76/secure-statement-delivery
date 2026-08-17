package com.example.statementservice.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.statementservice.statement.StatementStorageUnavailableException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3StatementFileStoreTest {

    private static final String BUCKET = "statements";

    @Mock
    private S3Client s3Client;

    private S3StatementFileStore fileStore;

    private ListAppender<ILoggingEvent> appender;
    private Logger fileStoreLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        var properties = new S3StorageProperties();
        properties.setBucket(BUCKET);
        properties.setRegion("eu-west-1");
        fileStore = new S3StatementFileStore(s3Client, properties);

        fileStoreLogger = (Logger) LoggerFactory.getLogger(S3StatementFileStore.class);
        originalLevel = fileStoreLogger.getLevel();
        fileStoreLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        fileStoreLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        fileStoreLogger.detachAppender(appender);
        fileStoreLogger.setLevel(originalLevel);
    }

    @Test
    void GivenContent_WhenStored_ThenPutObjectIsCalledWithBuiltKeyAndContentAndKeyIsReturned() throws IOException {
        // Given
        var id = UUID.randomUUID();
        var content = "encrypted-bytes".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // When
        var reference = fileStore.store(id, "123456789", LocalDate.of(2026, 7, 1), out -> out.write(content));

        // Then
        var requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(reference);
        assertThat(reference).endsWith(id + ".pdf.enc").contains("/2026/07/");
        assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
                .isEqualTo(content);
    }

    @Test
    void GivenSameAccountAndDate_WhenStoringTwice_ThenKeysDifferButShareTheSamePrefix() throws IOException {
        // Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        var accountNumber = "987654321";
        var date = LocalDate.of(2026, 3, 15);

        // When
        var first = fileStore.store(UUID.randomUUID(), accountNumber, date, out -> out.write("a".getBytes()));
        var second = fileStore.store(UUID.randomUUID(), accountNumber, date, out -> out.write("b".getBytes()));

        // Then
        assertThat(first).isNotEqualTo(second);
        assertThat(first.substring(0, first.lastIndexOf('/'))).isEqualTo(second.substring(0, second.lastIndexOf('/')));
    }

    @Test
    void GivenDifferentStatementDates_WhenStoring_ThenKeysAreSplitByYearAndMonth() throws IOException {
        // Given
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        var accountNumber = "111222333";

        // When
        var januaryRef = fileStore.store(
                UUID.randomUUID(), accountNumber, LocalDate.of(2026, 1, 10), out -> out.write("x".getBytes()));
        var decemberRef = fileStore.store(
                UUID.randomUUID(), accountNumber, LocalDate.of(2026, 12, 10), out -> out.write("y".getBytes()));

        // Then
        assertThat(januaryRef).contains("/2026/01/");
        assertThat(decemberRef).contains("/2026/12/");
    }

    @Test
    void GivenPutObjectFails_WhenStoring_ThenExceptionMessageHidesDetailsButLogHasThem() {
        // Given
        var failure = S3Exception.builder()
                .statusCode(500)
                .message("internal failure")
                .build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(failure);

        // When / Then
        assertThatThrownBy(() -> fileStore.store(
                        UUID.randomUUID(), "123456789", LocalDate.of(2026, 7, 1), out -> out.write("x".getBytes())))
                .isInstanceOf(IOException.class)
                .extracting(Throwable::getMessage)
                .asString()
                .doesNotContain(BUCKET)
                .doesNotContain("internal failure");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(BUCKET));
    }

    @Test
    void GivenReference_WhenOpened_ThenGetObjectIsCalledWithSameBucketAndKeyAndStreamIsReturned() throws IOException {
        // Given
        var reference = "statements/abc123/2026/07/some-id.pdf.enc";
        var content = "decrypted-bytes".getBytes();
        var responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new java.io.ByteArrayInputStream(content)));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        // When
        var result = fileStore.open(reference);

        // Then
        assertThat(result.readAllBytes()).isEqualTo(content);
        var requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(reference);
    }

    @Test
    void GivenMissingReference_WhenOpened_ThenFileNotFoundExceptionIsThrown() {
        // Given
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        // When / Then
        assertThatThrownBy(() -> fileStore.open("statements/missing/2026/07/x.pdf.enc"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void GivenStorageIsUnreachable_WhenOpening_ThenStatementStorageUnavailableExceptionIsThrown() {
        // Given: a non-NoSuchKey S3 failure during GetObject is an outage, not a decryption
        // problem - it must surface as storage-unavailable, mirroring exists() (ADR 0021).
        var failure = S3Exception.builder()
                .statusCode(503)
                .message("service unavailable")
                .build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(failure);

        // When / Then
        assertThatThrownBy(() -> fileStore.open("statements/abc123/2026/07/some-id.pdf.enc"))
                .isInstanceOf(StatementStorageUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void GivenReferenceExists_WhenCheckingExistence_ThenTrueIsReturned() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        // When / Then
        assertThat(fileStore.exists("statements/abc123/2026/07/some-id.pdf.enc"))
                .isTrue();
    }

    @Test
    void GivenReferenceMissing_WhenCheckingExistence_ThenFalseIsReturned() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        // When / Then
        assertThat(fileStore.exists("statements/missing/2026/07/x.pdf.enc")).isFalse();
    }

    @Test
    void GivenStorageIsUnreachable_WhenCheckingExistence_ThenStatementStorageUnavailableExceptionIsThrown() {
        // Given: a non-NoSuchKey S3 failure must not be miscategorized as "file missing" (ADR 0021),
        // and must not leak the raw SDK exception type out of the storage adapter either.
        var failure = S3Exception.builder()
                .statusCode(503)
                .message("service unavailable")
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        // When / Then
        assertThatThrownBy(() -> fileStore.exists("statements/abc123/2026/07/some-id.pdf.enc"))
                .isInstanceOf(StatementStorageUnavailableException.class)
                .hasCause(failure);
    }
}
