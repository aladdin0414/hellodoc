package com.nopkg.hellodoc.storage;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.ApiResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public class S3StorageClient implements StorageClient {

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public S3StorageClient(
            String bucket,
            String region,
            String endpoint,
            boolean pathStyleAccessEnabled,
            String accessKeyId,
            String secretKey) {
        if (bucket == null || bucket.isBlank()) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "bucket 不能为空");
        }
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "accessKeyId 不能为空");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "secretKey 不能为空");
        }
        this.bucket = bucket;

        Region r = Region.of((region == null || region.isBlank()) ? "us-east-1" : region.trim());
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                accessKeyId.trim(),
                secretKey));

        var s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccessEnabled)
                .build();

        var builder = S3Client.builder()
                .region(r)
                .credentialsProvider(creds)
                .serviceConfiguration(s3Config);

        var presignerBuilder = S3Presigner.builder()
                .region(r)
                .credentialsProvider(creds)
                .serviceConfiguration(s3Config);

        if (endpoint != null && !endpoint.isBlank()) {
            URI uri = URI.create(endpoint.trim());
            builder = builder.endpointOverride(uri);
            presignerBuilder = presignerBuilder.endpointOverride(uri);
        }

        this.s3 = builder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public String upload(InputStream is, String key, String contentType) {
        try {
            byte[] bytes = is.readAllBytes();
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(key))
                    .contentType(contentType)
                    .build();
            s3.putObject(req, RequestBody.fromBytes(bytes));
            return key;
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "S3 上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(key))
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "S3 删除失败: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration) {
        return generatePresignedUrl(key, expiration, null);
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration, String filename) {
        try {
            GetObjectRequest.Builder getBuilder = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(key));

            if (filename != null && !filename.isBlank()) {
                String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
                getBuilder.responseContentDisposition("inline; filename*=UTF-8''" + encoded);
            }

            GetObjectRequest get = getBuilder.build();
            GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(get)
                    .build();
            return presigner.presignGetObject(presign).url().toString();
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "S3 生成预签名URL失败: " + e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(key))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeKey(key))
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "S3 下载失败: " + e.getMessage());
        }
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "key 不能为空");
        }
        String k = key.trim();
        String normalized = k.startsWith("/") ? k.substring(1) : k;
        if (normalized.isBlank()) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "key 不能为空");
        }
        return normalized;
    }
}
