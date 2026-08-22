package com.example.statementservice.shared;

import java.io.IOException;
import java.io.InputStream;

public interface ContentDigest {

    String hexOf(byte[] content);

    // Caller owns and closes the stream.
    String hexOf(InputStream content) throws IOException;
}
