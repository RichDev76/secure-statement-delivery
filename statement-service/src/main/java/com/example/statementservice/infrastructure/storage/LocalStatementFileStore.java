package com.example.statementservice.infrastructure.storage;

import com.example.statementservice.shared.Sha256Digest;
import com.example.statementservice.shared.StatementUploadException;
import com.example.statementservice.statement.StatementFileStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalStatementFileStore implements StatementFileStore {

    public static final String FILE_EXTENSION_PDF_ENC = ".pdf.enc";
    public static final String STATEMENTS_FOLDER = "statements";

    @Value("${statement.storage.base-dir:/data/files}")
    private String baseDir;

    @Override
    public String store(UUID id, String accountNumber, LocalDate statementDate, ContentWriter writer)
            throws IOException {
        var accountNumberHash = Sha256Digest.hexOf(accountNumber.trim().getBytes(StandardCharsets.UTF_8));
        var directory = resolveDirectory(accountNumberHash, statementDate);
        var file = new File(directory, id + FILE_EXTENSION_PDF_ENC);
        try (var out = new FileOutputStream(file)) {
            writer.writeTo(out);
        }
        return file.getAbsolutePath();
    }

    @Override
    public InputStream open(String reference) throws IOException {
        return new FileInputStream(reference);
    }

    @Override
    public boolean exists(String reference) {
        return new File(reference).exists();
    }

    private File resolveDirectory(String accountNumberHash, LocalDate statementDate) {
        var year = Integer.toString(statementDate.getYear());
        var month = String.format(Locale.ROOT, "%02d", statementDate.getMonthValue());
        var directory = new File(
                new File(new File(new File(baseDir), STATEMENTS_FOLDER), accountNumberHash),
                new File(year, month).getPath());
        if (!directory.exists() && !directory.mkdirs()) {
            log.error("Failed to create storage directory: {}", directory.getAbsolutePath());
            throw new StatementUploadException("Failed to create storage directory");
        }
        return directory;
    }
}
