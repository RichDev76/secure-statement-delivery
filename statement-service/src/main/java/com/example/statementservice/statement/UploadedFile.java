package com.example.statementservice.statement;

import java.io.IOException;
import java.io.InputStream;

// getInputStream() must be safely callable more than once - upload reads it for validation, digest, and encryption.
public interface UploadedFile {

    boolean isEmpty();

    String getContentType();

    String getOriginalFilename();

    long getSize();

    InputStream getInputStream() throws IOException;
}
