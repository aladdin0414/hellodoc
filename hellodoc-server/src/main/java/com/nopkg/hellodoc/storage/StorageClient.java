package com.nopkg.hellodoc.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageClient {
    String upload(InputStream is, String key, String contentType);

    void delete(String key);

    String generatePresignedUrl(String key, Duration expiration);
    String generatePresignedUrl(String key, Duration expiration, String filename);

    boolean exists(String key);

    InputStream download(String key);
}
