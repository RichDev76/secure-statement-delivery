package com.example.statementservice.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.statementservice.api.StatementsApi;
import com.example.statementservice.shared.IdGeneratorPort;
import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.upload.UploadResponseDto;
import jakarta.validation.constraints.Pattern;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatementService Unit Tests")
class StatementServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-11T12:00:00Z");

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private StatementFileStore fileStore;

    @Mock
    private FileCipher fileCipher;

    @Mock
    private StatementEntityMapper statementEntityMapper;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private IdGeneratorPort idGenerator;

    @Mock
    private EncryptedFileFetcher encryptedFileFetcher;

    @InjectMocks
    private StatementService statementService;

    private Statement testStatement;
    private StatementDto testStatementDto;
    private UUID testId;
    private String testAccountNumber;
    private LocalDate testStatementDate;
    private String testContentHash;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAccountNumber = "123456789";
        testStatementDate = LocalDate.of(2024, 1, 1);
        testContentHash = Sha256Digest.hexOf("file-content".getBytes());
        testStatement = new Statement();
        testStatement.setId(testId);
        testStatement.setAccountNumber(testAccountNumber);
        testStatement.setStatementDate(testStatementDate);
        testStatement.setUploadFileName("statement.pdf");
        testStatement.setStorageKey("statements/hash/2026/07/statement.pdf.enc");
        testStatement.setSizeBytes(1024L);
        testStatement.setUploadedAt(OffsetDateTime.now());
        testStatement.setUploadedBy("admin");
        testStatement.setEncrypted(true);
        testStatement.setContentHash("abc123");
        testStatementDto = StatementDto.builder()
                .statementId(testId)
                .accountNumber(testAccountNumber)
                .statementDate(testStatementDate)
                .build();
    }

    private void stubSuccessfulEncryptAndStore(byte[] iv, byte[] content) throws Exception {
        when(idGenerator.newId()).thenReturn(testId);
        when(fileCipher.generateInitializationVector()).thenReturn(iv);
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(content));
        when(fileStore.store(any(UUID.class), eq(testAccountNumber), eq(testStatementDate), any()))
                .thenAnswer(invocation -> {
                    StatementFileStore.ContentWriter writer = invocation.getArgument(3);
                    var out = new ByteArrayOutputStream();
                    writer.writeTo(out);
                    return "/data/files/statements/hash/2024/01/" + invocation.getArgument(0) + ".pdf.enc";
                });
    }

    @Test
    void GivenValidUpload_WhenUploadStatement_ThenPersistsAndReturnsResponse() throws Exception {
        String uploadedBy = "testUser";
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);
        stubSuccessfulEncryptAndStore(mockIv, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> i.getArgument(0));
        UploadResponseDto result = statementService.uploadStatement(
                testAccountNumber, testStatementDate, multipartFile, uploadedBy, testContentHash);
        assertThat(result).isNotNull();
        assertThat(result.statementId()).isNotNull();
        assertThat(result.fileName()).isEqualTo("statement.pdf");
        assertThat(result.fileSize()).isEqualTo(2048L);
        assertThat(result.uploadedAt()).isNotNull();
        verify(fileStore).store(any(UUID.class), eq(testAccountNumber), eq(testStatementDate), any());
        verify(statementRepository).saveAndFlush(any(Statement.class));
    }

    @Test
    void GivenNullUploadedBy_WhenUploadStatement_ThenAdminIsPersistedAsUploader() throws Exception {
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);
        stubSuccessfulEncryptAndStore(mockIv, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> {
            Statement stmt = i.getArgument(0);
            assertThat(stmt.getUploadedBy()).isEqualTo("admin");
            return stmt;
        });
        statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, null, testContentHash);
        verify(statementRepository).saveAndFlush(any(Statement.class));
    }

    @Test
    void GivenRepositoryFailure_WhenUploadStatement_ThenThrowsStatementUploadException() throws Exception {
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);
        stubSuccessfulEncryptAndStore(mockIv, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB error"));
        assertThatThrownBy(() -> statementService.uploadStatement(
                        testAccountNumber, testStatementDate, multipartFile, "user", testContentHash))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("Failed to persist statement metadata");
    }

    @Test
    void GivenStatementForSameAccountAndDateExists_WhenUploadStatement_ThenThrowsDuplicateStatementException()
            throws Exception {
        // Given
        when(statementRepository.existsByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> statementService.uploadStatement(
                        testAccountNumber, testStatementDate, multipartFile, "user", testContentHash))
                .isInstanceOf(DuplicateStatementException.class);
        verify(fileStore, never()).store(any(UUID.class), any(), any(), any());
        verify(statementRepository, never()).saveAndFlush(any());
    }

    @Test
    void GivenConcurrentDuplicateInsert_WhenUploadStatement_ThenThrowsDuplicateStatementException() throws Exception {
        // Given: the pre-check misses but the unique index fires on save
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        stubSuccessfulEncryptAndStore(mockIv, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"idx_statements_account_date\""));

        // When / Then
        assertThatThrownBy(() -> statementService.uploadStatement(
                        testAccountNumber, testStatementDate, multipartFile, "user", testContentHash))
                .isInstanceOf(DuplicateStatementException.class);
    }

    @Test
    void GivenUnrelatedIntegrityViolation_WhenUploadStatement_ThenThrowsStatementUploadException() throws Exception {
        // Given: an integrity failure that is not the account/date unique index
        byte[] mockIv = new byte[] {1, 2, 3, 4};
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        stubSuccessfulEncryptAndStore(mockIv, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "null value in column \"content_hash\" violates not-null constraint"));

        // When / Then
        assertThatThrownBy(() -> statementService.uploadStatement(
                        testAccountNumber, testStatementDate, multipartFile, "user", testContentHash))
                .isInstanceOf(StatementUploadException.class);
    }

    @Test
    void GivenDekWrapFailure_WhenUploadStatement_ThenThrowsStatementUploadException() {
        // Given: a master-key problem surfaces as FileCipherException from wrapDek
        when(idGenerator.newId()).thenReturn(testId);
        when(fileCipher.generateInitializationVector()).thenReturn(new byte[] {1, 2, 3, 4});
        when(fileCipher.generateDek()).thenReturn(new byte[] {9, 9, 9, 9});
        when(fileCipher.wrapDek(any())).thenThrow(new FileCipherException("Failed to wrap DEK"));

        // When / Then: classified as an upload failure, and nothing is stored or persisted
        assertThatThrownBy(() -> statementService.uploadStatement(
                        testAccountNumber, testStatementDate, multipartFile, "user", testContentHash))
                .isInstanceOf(StatementUploadException.class)
                .hasMessageContaining("Failed to prepare encryption key");
        verify(statementRepository, never()).saveAndFlush(any());
    }

    @Test
    void GivenValidUpload_WhenUploadStatement_ThenGeneratesDekAndPersistsItsWrappedFormNotTheRawDek() throws Exception {
        // Given
        var dek = new byte[] {9, 9, 9, 9};
        var wrappedDek = new byte[] {8, 8, 8, 8, 8};
        when(fileCipher.generateDek()).thenReturn(dek);
        when(fileCipher.wrapDek(dek)).thenReturn(wrappedDek);
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);
        stubSuccessfulEncryptAndStore(new byte[] {1, 2, 3, 4}, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> i.getArgument(0));

        // When
        statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, "user", testContentHash);

        // Then
        var captor = org.mockito.ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEncryptedDek()).isEqualTo(wrappedDek).isNotEqualTo(dek);
        verify(fileCipher).encrypt(any(), any(), eq(new byte[] {1, 2, 3, 4}), eq(dek));
    }

    @Test
    void GivenPrecomputedContentHash_WhenUploadStatement_ThenPersistsThatHashWithoutRereadingFile() throws Exception {
        // Given
        when(multipartFile.getOriginalFilename()).thenReturn("statement.pdf");
        when(multipartFile.getSize()).thenReturn(2048L);
        stubSuccessfulEncryptAndStore(new byte[] {1, 2, 3, 4}, "file-content".getBytes());
        when(statementRepository.saveAndFlush(any(Statement.class))).thenAnswer(i -> i.getArgument(0));

        // When
        statementService.uploadStatement(testAccountNumber, testStatementDate, multipartFile, "user", testContentHash);

        // Then
        var captor = org.mockito.ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContentHash()).isEqualTo(testContentHash);
        verify(multipartFile, never()).getBytes();
    }

    @Test
    void GivenStatementWithWrappedDek_WhenOpenDecryptedFile_ThenUnwrapsDekBeforeDecrypting() throws Exception {
        // Given: ciphertext is fetched via EncryptedFileFetcher (possibly cached), never
        // StatementFileStore directly
        var wrappedDek = new byte[] {8, 8, 8, 8, 8};
        var dek = new byte[] {9, 9, 9, 9};
        testStatement.setEncryptedDek(wrappedDek);
        var ciphertext = "ciphertext".getBytes();
        var plaintext = "plaintext".getBytes();
        when(fileCipher.unwrapDek(wrappedDek)).thenReturn(dek);
        when(encryptedFileFetcher.fetch(testStatement.getStorageKey())).thenReturn(ciphertext);
        when(fileCipher.decrypt(ciphertext, dek)).thenReturn(plaintext);

        // When
        var result = statementService.openDecryptedFile(testStatement);

        // Then
        assertThat(result.readAllBytes()).isEqualTo(plaintext);
        verify(fileCipher).unwrapDek(wrappedDek);
        verify(fileCipher).decrypt(ciphertext, dek);
        verify(fileStore, never()).open(any());
    }

    @Test
    void GivenExistingId_WhenGettingStatementById_ThenReturnsStatement() {
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.of(testStatement));
        Statement result = statementService.getStatementById(testId);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testId);
        assertThat(result.getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findStatementById(testId);
    }

    @Test
    void GivenUnknownId_WhenGettingStatementById_ThenThrowsStatementNotFoundException() {
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> statementService.getStatementById(testId))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement not found for id: " + testId);
        verify(statementRepository).findStatementById(testId);
    }

    @Test
    void GivenPageable_WhenGettingStatementsByAccountNumber_ThenReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Statement> page = new PageImpl<>(Arrays.asList(testStatement));
        when(statementRepository.findByAccountNumber(testAccountNumber, pageable))
                .thenReturn(page);
        Page<Statement> result = statementService.getStatementsByAccountNumber(testAccountNumber, pageable);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findByAccountNumber(testAccountNumber, pageable);
    }

    @Test
    void GivenExistingAccount_WhenGettingStatementsByAccountNumber_ThenReturnsList() {
        List<Statement> statements = Arrays.asList(testStatement);
        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.of(statements));
        List<Statement> result = statementService.getStatementsByAccountNumber(testAccountNumber);
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo(testAccountNumber);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
    }

    @Test
    void GivenUnknownAccount_WhenGettingStatementsByAccountNumber_ThenThrowsStatementNotFoundException() {
        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> statementService.getStatementsByAccountNumber(testAccountNumber))
                .isInstanceOf(StatementNotFoundException.class)
                .hasMessageContaining("Statement(s) not found for account number: " + testAccountNumber);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
    }

    @Test
    void GivenExistingAccountAndDate_WhenGettingStatement_ThenReturnsIt() {
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.of(testStatement));
        Optional<Statement> result =
                statementService.getStatementByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        assertThat(result).isPresent();
        assertThat(result.get().getAccountNumber()).isEqualTo(testAccountNumber);
        assertThat(result.get().getStatementDate()).isEqualTo(testStatementDate);
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
    }

    @Test
    void GivenUnknownAccountAndDate_WhenGettingStatement_ThenReturnsEmpty() {
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.empty());
        Optional<Statement> result =
                statementService.getStatementByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        assertThat(result).isEmpty();
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
    }

    @Test
    void GivenStatementEntity_WhenMappingToDto_ThenMapperResultIsReturned() {
        when(statementEntityMapper.toDto(testStatement)).thenReturn(testStatementDto);
        StatementDto result = statementService.toDto(testStatement);
        assertThat(result).isNotNull();
        assertThat(result.statementId()).isEqualTo(testId);
        verify(statementEntityMapper).toDto(testStatement);
    }

    @Test
    void GivenExistingId_WhenGettingStatementDtoById_ThenReturnsDto() {
        when(statementRepository.findStatementById(testId)).thenReturn(Optional.of(testStatement));
        when(statementEntityMapper.toDto(testStatement)).thenReturn(testStatementDto);
        StatementDto result = statementService.getStatementDtoById(testId);
        assertThat(result).isNotNull();
        assertThat(result.statementId()).isEqualTo(testId);
        verify(statementRepository).findStatementById(testId);
        verify(statementEntityMapper).toDto(testStatement);
    }

    @Test
    void GivenExistingAccount_WhenGettingStatementDtos_ThenReturnsDtos() {
        List<Statement> statements = Arrays.asList(testStatement);
        List<StatementDto> dtos = Arrays.asList(testStatementDto);
        when(statementRepository.findAllByAccountNumber(testAccountNumber)).thenReturn(Optional.of(statements));
        when(statementEntityMapper.toDtos(statements)).thenReturn(dtos);
        List<StatementDto> result = statementService.getStatementsDtoByAccountNumber(testAccountNumber);
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).statementId()).isEqualTo(testId);
        verify(statementRepository).findAllByAccountNumber(testAccountNumber);
        verify(statementEntityMapper).toDtos(statements);
    }

    @Test
    void GivenExistingAccountAndDate_WhenGettingStatementDto_ThenReturnsDto() {
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.of(testStatement));
        when(statementEntityMapper.toDto(testStatement)).thenReturn(testStatementDto);
        Optional<StatementDto> result =
                statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        assertThat(result).isPresent();
        assertThat(result.get().statementId()).isEqualTo(testId);
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        verify(statementEntityMapper).toDto(testStatement);
    }

    @Test
    void GivenUnknownAccountAndDate_WhenGettingStatementDto_ThenReturnsEmpty() {
        when(statementRepository.findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate))
                .thenReturn(Optional.empty());
        Optional<StatementDto> result =
                statementService.getStatementDtoByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        assertThat(result).isEmpty();
        verify(statementRepository).findByAccountNumberAndStatementDate(testAccountNumber, testStatementDate);
        verify(statementEntityMapper, never()).toDto(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "statement.pdf",
                "My.Statement.PDF",
                "a..b.pdf",
                "state ment.pdf",
                "ümlaut-März.PDF",
                "statement-2026-01.PDF",
                "no-extension",
                ".pdf",
                "report.bin"
            })
    void GivenAnyOriginalFilename_WhenSanitized_ThenOutputMatchesDownloadContractPattern(String originalFilename) {
        // When
        var sanitized = StatementService.sanitizeFileName(originalFilename);

        // Then
        assertThat(sanitized).matches(DOWNLOAD_FILE_NAME_CONTRACT_PATTERN);
    }

    private static final String DOWNLOAD_FILE_NAME_CONTRACT_PATTERN = downloadFileNameContractPattern();

    private static String downloadFileNameContractPattern() {
        return Arrays.stream(StatementsApi.class.getMethods())
                .filter(method -> method.getName().equals("downloadStatementByFileName"))
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(parameter -> parameter.getAnnotation(Pattern.class))
                .filter(Objects::nonNull)
                .map(Pattern::regexp)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No @Pattern found on downloadStatementByFileName fileName parameter"));
    }
}
