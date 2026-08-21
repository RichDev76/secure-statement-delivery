package com.example.statementservice.statement.upload.infrastructure;

import com.example.statementservice.statement.UploadedFile;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public final class MultipartFileAdapter implements UploadedFile {

    private final MultipartFile delegate;

    public MultipartFileAdapter(MultipartFile delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public String getOriginalFilename() {
        return delegate.getOriginalFilename();
    }

    @Override
    public long getSize() {
        return delegate.getSize();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }
}
