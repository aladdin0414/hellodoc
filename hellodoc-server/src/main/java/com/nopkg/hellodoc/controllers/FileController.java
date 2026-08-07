package com.nopkg.hellodoc.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload and download APIs")
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/avatars/{filename}")
    @Operation(summary = "Get avatar", description = "Get user avatar image")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {

        try {
            // 校验文件名，只允许字母、数字、点、下划线、横线
            if (!filename.matches("^[a-zA-Z0-9._-]+$")) {
                return ResponseEntity.badRequest().build();
            }

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve("avatars").resolve(filename).normalize();

            // 防止路径穿越
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // 根据文件扩展名确定 Content-Type
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/public/proxy-image")
    @Operation(summary = "Proxy external image", description = "Proxy external image URL for browser side document export to avoid CORS restriction")
    public ResponseEntity<Resource> proxyImage(@RequestParam("url") String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return ResponseEntity.badRequest().build();
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return ResponseEntity.badRequest().build();
            }

            URLConnection conn = uri.toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "HelloDoc-Image-Proxy/1.0");

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                return ResponseEntity.badRequest().build();
            }

            InputStream inputStream = conn.getInputStream();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=600")
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
