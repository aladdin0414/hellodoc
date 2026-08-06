package com.nopkg.hellodoc.storage;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.ApiResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

public class LocalStorageClient implements StorageClient {

    private final Path rootDir;
    private final StorageUrlSigner signer;

    public LocalStorageClient(Path rootDir, StorageUrlSigner signer) {
        this.rootDir = rootDir;
        this.signer = signer;
    }

    @Override
    public String upload(InputStream is, String key, String contentType) {
        try {
            Path target = resolveKeyToPath(key);
            Files.createDirectories(target.getParent());
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "本地存储上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path target = resolveKeyToPath(key);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "本地存储删除失败: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration) {
        return generatePresignedUrl(key, expiration, null);
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration, String filename) {
        long exp = Instant.now().plus(expiration).getEpochSecond();
        String sig = signer.sign(key, exp);
        String url = "/api/storage/public?key=" + urlEncode(key) + "&exp=" + exp + "&sig=" + sig;
        if (filename != null && !filename.isBlank()) {
            url += "&filename=" + urlEncode(filename);
        }
        return url;
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolveKeyToPath(key));
    }

    @Override
    public InputStream download(String key) {
        try {
            Path target = resolveKeyToPath(key);
            if (!Files.exists(target)) {
                throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "文件不存在");
            }
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "读取本地文件失败: " + e.getMessage());
        }
    }

    public Path resolveKeyToPath(String key) {
        String k = key == null ? "" : key;
        String normalized = k.startsWith("/") ? k.substring(1) : k;
        Path p = rootDir.resolve(normalized).normalize();
        if (!p.startsWith(rootDir.normalize())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "非法的存储键");
        }
        return p;
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
