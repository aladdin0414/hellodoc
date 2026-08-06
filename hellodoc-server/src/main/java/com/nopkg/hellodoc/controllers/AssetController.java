package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbAsset;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.AssetService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.io.ByteArrayOutputStream;
import com.nopkg.hellodoc.utils.ByteArrayMultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "附件管理", description = "附件上传与管理接口")
public class AssetController {

    private final AssetService assetService;
    private final KbService kbService;

    public record AssetVO(
            Long id,
            Long kbId,
            Long docId,
            Long storageFileId,
            Long uploaderId,
            String fileName,
            String description,
            String rawUrl,
            String downloadUrl,
            OffsetDateTime createdAt) {
    }

    public record UrlAssetRequest(String url) {}

    @PostMapping("/api/kb/{kbId}/assets")
    @RequireKbRole(com.nopkg.hellodoc.enums.KbRole.EDITOR)
    @Operation(summary = "上传知识库附件")
    public ApiResponse<AssetVO> uploadToKb(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "description", required = false) String description) {
        Long userId = currentUserId();
        KbAsset asset = assetService.uploadAsset(kbId, null, file, userId, fileName, description);
        return ApiResponse.success(toVo(asset));
    }

    @PostMapping("/api/kb/{kbId}/docs/{docId}/assets")
    @RequireKbRole(com.nopkg.hellodoc.enums.KbRole.EDITOR)
    @Operation(summary = "上传文档附件")
    public ApiResponse<AssetVO> uploadToDoc(
            @PathVariable Long kbId,
            @PathVariable Long docId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "description", required = false) String description) {
        Long userId = currentUserId();
        KbAsset asset = assetService.uploadAsset(kbId, docId, file, userId, fileName, description);
        return ApiResponse.success(toVo(asset));
    }

    @PostMapping("/api/kb/{kbId}/docs/{docId}/assets/from-url")
    @RequireKbRole(com.nopkg.hellodoc.enums.KbRole.EDITOR)
    @Operation(summary = "从外部URL抓取并作为文档附件上传")
    public ApiResponse<AssetVO> uploadToDocFromUrl(
            @PathVariable Long kbId,
            @PathVariable Long docId,
            @RequestBody UrlAssetRequest request) {
        String url = request.url();
        if (url == null || url.isBlank()) {
            return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "URL不能为空");
        }

        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "不支持的URL协议");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "无效的主机名");
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "禁止访问内网地址");
            }

            URLConnection conn = uri.toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "HelloDoc-Image-Proxy/1.0");

            String contentType = conn.getContentType();
            if (contentType == null || (!contentType.toLowerCase().startsWith("image/") && !contentType.toLowerCase().startsWith("video/"))) {
                return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "只支持抓取图片或视频内容");
            }

            String path = uri.getPath();
            String fileName = "file";
            if (path != null && path.contains("/")) {
                fileName = path.substring(path.lastIndexOf('/') + 1);
            }
            if (!fileName.contains(".")) {
                String ext = "bin";
                if (contentType.contains("/")) {
                    ext = contentType.split("/")[1].split(";")[0];
                }
                fileName += "." + ext;
            }

            // 读取到字节数组
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] data = new byte[8192];
                int nRead;
                int totalRead = 0;
                int maxSize = 50 * 1024 * 1024; // 限制最大 50MB
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                    totalRead += nRead;
                    if (totalRead > maxSize) {
                        return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, "文件过大，无法抓取");
                    }
                }
            }

            byte[] fileContent = buffer.toByteArray();
            ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(fileContent, "file", fileName, contentType);

            Long userId = currentUserId();
            KbAsset asset = assetService.uploadAsset(kbId, docId, multipartFile, userId, fileName, null);
            return ApiResponse.success(toVo(asset));
        } catch (Exception e) {
            return ApiResponse.error(ApiResponse.Code.SYSTEM_ERROR, "抓取URL失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/kb/{kbId}/assets")
    @RequireKbRole(com.nopkg.hellodoc.enums.KbRole.VIEWER)
    @Operation(summary = "知识库附件列表")
    public ApiResponse<List<AssetVO>> listKbAssets(@PathVariable Long kbId) {
        Long userId = currentUserIdOrNull();
        return ApiResponse.success(assetService.getKbAssets(kbId, userId).stream().map(this::toVo).toList());
    }

    @GetMapping("/api/docs/{docId}/assets")
    @RequireDocRole(com.nopkg.hellodoc.enums.DocRole.VIEWER)
    @Operation(summary = "文档附件列表")
    public ApiResponse<List<AssetVO>> listDocAssets(@PathVariable Long docId) {
        Long userId = currentUserIdOrNull();
        return ApiResponse.success(assetService.getDocumentAssets(docId, userId).stream().map(this::toVo).toList());
    }

    @GetMapping("/api/assets/{id}")
    @Operation(summary = "附件详情")
    public ApiResponse<AssetVO> getAsset(@PathVariable Long id) {
        Long userId = currentUserIdOrNull();
        return ApiResponse.success(toVo(assetService.getAsset(id, userId)));
    }

    @GetMapping("/api/assets/{id}/url")
    @Operation(summary = "获取附件访问URL")
    public ApiResponse<Map<String, String>> getAssetUrl(@PathVariable Long id) {
        Long userId = currentUserIdOrNull();
        String url = assetService.getAssetUrl(id, userId);
        return ApiResponse.success(Map.of("url", url));
    }

    @GetMapping("/api/assets/{id}/raw")
    @Operation(summary = "获取附件原始内容")
    public ResponseEntity<Void> getAssetRaw(@PathVariable Long id) {
        String url = assetService.getAssetUrlPublic(id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @GetMapping("/api/assets/{id}/download")
    @Operation(summary = "下载附件")
    public ResponseEntity<Resource> downloadAsset(@PathVariable Long id) {
        InputStream is = assetService.getAssetContentPublic(id);
        KbAsset asset = assetService.getAssetPublic(id);

        String contentType = asset.getStorageFile().getFileType();
        MediaType mediaType = (contentType == null) ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);

        String fileName = asset.getFileName();
        String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(new InputStreamResource(is));
    }

    @DeleteMapping("/api/assets/{id}")
    @Operation(summary = "删除附件")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id) {
        Long userId = currentUserId();
        assetService.deleteAsset(id, userId);
        return ApiResponse.success();
    }

    private AssetVO toVo(KbAsset asset) {
        return new AssetVO(
                asset.getId(),
                asset.getKb().getId(),
                asset.getDoc() == null ? null : asset.getDoc().getId(),
                asset.getStorageFile().getId(),
                asset.getUploaderId(),
                asset.getFileName(),
                asset.getDescription(),
                "/api/assets/" + asset.getId() + "/raw",
                "/api/assets/" + asset.getId() + "/download",
                asset.getCreatedAt());
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return kbService.requireUserId(username);
    }

    private Long currentUserIdOrNull() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || "anonymousUser".equals(username)) {
            return null;
        }
        return kbService.requireUserId(username);
    }
}
