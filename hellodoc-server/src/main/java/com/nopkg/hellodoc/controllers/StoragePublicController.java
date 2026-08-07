package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.utils.MessageUtils;
import com.nopkg.hellodoc.storage.StorageUrlSigner;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Tag(name = "Public Storage APIs", description = "Public signed URL access APIs for private storage files")
public class StoragePublicController {

    private final StorageUrlSigner signer;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private static final long MAX_REGION_SIZE = 10L * 1024 * 1024;

    @GetMapping("/public")
    @Operation(summary = "Get public resource", description = "Read local server storage file after validating signature and expiration")
    public ResponseEntity<?> getPublic(
            @RequestParam String key,
            @RequestParam long exp,
            @RequestParam String sig,
            @RequestParam(required = false) String filename,
            @RequestHeader HttpHeaders requestHeaders) {
        long now = Instant.now().getEpochSecond();
        if (exp < now) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("auth.link_expired", "Link has expired"));
        }
        String expected = signer.sign(key, exp);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION, MessageUtils.get("auth.invalid_signature", "Invalid signature"));
        }

        Path rootDir = Paths.get(uploadDir, "storage").normalize();
        Path file = resolveKeyToPath(rootDir, key);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, MessageUtils.get("common.file_not_found", "File not found"));
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            String contentType = Files.probeContentType(file);
            MediaType mediaType = (contentType == null) ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);

            long contentLength = Files.size(file);
            String rangeHeader = requestHeaders.getFirst(HttpHeaders.RANGE);
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    HttpRange range = ranges.get(0);
                    long start = range.getRangeStart(contentLength);
                    long end = range.getRangeEnd(contentLength);
                    long rangeLength = end - start + 1;
                    long responseLength = Math.min(rangeLength, MAX_REGION_SIZE);
                    long responseEnd = start + responseLength - 1;
                    if (start < 0 || start >= contentLength || responseEnd < start) {
                        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                                .build();
                    }

                    InputStream base = Files.newInputStream(file);
                    try {
                        long toSkip = start;
                        while (toSkip > 0) {
                            long skipped = base.skip(toSkip);
                            if (skipped <= 0) {
                                break;
                            }
                            toSkip -= skipped;
                        }
                    } catch (Exception e) {
                        base.close();
                        throw e;
                    }

                    InputStream limited = new LimitedInputStream(base, responseLength);
                    InputStreamResource body = new InputStreamResource(limited);

                    var builder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .contentType(mediaType)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .header(HttpHeaders.CONTENT_RANGE,
                                    "bytes " + start + "-" + responseEnd + "/" + contentLength)
                            .contentLength(responseLength)
                            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60");

                    if (filename != null && !filename.isBlank()) {
                        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                                .replace("+", "%20");
                        builder.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
                    }

                    return builder.body(body);
                }
            }

            var builder = ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60");

            if (filename != null && !filename.isBlank()) {
                String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
                builder.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
            }

            return builder.body(resource);
        } catch (Exception e) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.read_failed", "Failed to read file: ") + e.getMessage());
        }
    }

    private Path resolveKeyToPath(Path rootDir, String key) {
        String k = key == null ? "" : key;
        String normalized = k.startsWith("/") ? k.substring(1) : k;
        Path p = rootDir.resolve(normalized).normalize();
        if (!p.startsWith(rootDir)) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("storage.invalid_key", "Invalid storage key"));
        }
        return p;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        private LimitedInputStream(InputStream in, long remaining) {
            super(in);
            this.remaining = Math.max(remaining, 0);
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = super.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
