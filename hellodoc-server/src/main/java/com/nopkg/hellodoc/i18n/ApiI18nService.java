package com.nopkg.hellodoc.i18n;

import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApiI18nService {

    private static final Pattern REQUIRED_EN_PATTERN = Pattern.compile("^(.+) is required$");
    private static final Pattern REQUIRED_ZH_PATTERN = Pattern.compile("^(.+) 不能为空$");
    private static final Pattern MISSING_KB_ROLE_PATTERN = Pattern.compile("^需要知识库角色: (.+)$");
    private static final Pattern MISSING_DOC_ROLE_PATTERN = Pattern.compile("^需要文档角色: (.+)$");
    private static final Pattern PARAMETER_ID_PATTERN = Pattern.compile("^在参数中未找到(.+?) ID$");
    private static final Pattern DICT_VALUE_EXISTS_PATTERN = Pattern.compile("^该字典类型下已存在相同的字典键值: (.+)$");
    private static final Pattern UNSUPPORTED_PROVIDER_PATTERN = Pattern.compile("^暂不支持的存储提供商: (.+)$");

    private final MessageSource messageSource;

    public String resolveCodeMessage(ApiResponse.Code code) {
        return getMessage("api.code." + code.name(), code.message());
    }

    public String resolveMessage(ApiResponse.Code code, String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return resolveCodeMessage(code);
        }

        String message = rawMessage.trim();
        if (message.equals(code.message())) {
            return resolveCodeMessage(code);
        }

        String resolved = resolveLegacyMessage(message);
        return resolved != null ? resolved : message;
    }

    private String resolveLegacyMessage(String message) {
        Matcher requiredEn = REQUIRED_EN_PATTERN.matcher(message);
        if (requiredEn.matches()) {
            return getMessage("legacy.field.required", "{0} is required", requiredEn.group(1));
        }

        Matcher requiredZh = REQUIRED_ZH_PATTERN.matcher(message);
        if (requiredZh.matches()) {
            return getMessage("legacy.field.required", "{0} is required", requiredZh.group(1));
        }

        Matcher kbRole = MISSING_KB_ROLE_PATTERN.matcher(message);
        if (kbRole.matches()) {
            return getMessage("legacy.permission.required_kb_role", "Required knowledge base role: {0}", kbRole.group(1));
        }

        Matcher docRole = MISSING_DOC_ROLE_PATTERN.matcher(message);
        if (docRole.matches()) {
            return getMessage("legacy.permission.required_doc_role", "Required document role: {0}", docRole.group(1));
        }

        Matcher parameterId = PARAMETER_ID_PATTERN.matcher(message);
        if (parameterId.matches()) {
            return getMessage("legacy.permission.parameter_id_missing", "Missing {0} ID in parameters", parameterId.group(1));
        }

        Matcher dictValueExists = DICT_VALUE_EXISTS_PATTERN.matcher(message);
        if (dictValueExists.matches()) {
            return getMessage("legacy.dict.value_exists", "The dictionary value already exists: {0}", dictValueExists.group(1));
        }

        Matcher unsupportedProvider = UNSUPPORTED_PROVIDER_PATTERN.matcher(message);
        if (unsupportedProvider.matches()) {
            return getMessage("legacy.storage.unsupported_provider", "Unsupported storage provider: {0}", unsupportedProvider.group(1));
        }

        String prefixed = resolvePrefixedMessage(message);
        if (prefixed != null) {
            return prefixed;
        }

        return switch (message) {
            case "AI API Key is not configured." -> getMessage("ai.key_not_configured", message);
            case "AI response parsing failed or empty choices." -> getMessage("ai.response_error", message);
            case "Document not found in kb" -> getMessage("legacy.document.not_found_in_kb", "Document not found in knowledge base");
            case "User not found", "用户不存在" -> getMessage("legacy.user.not_found", "User not found");
            case "Document not found" -> getMessage("legacy.document.not_found", "Document not found");
            case "Cannot change role of the owner" -> getMessage("legacy.kb_member.cannot_change_owner", "Cannot change role of the owner");
            case "Cannot remove the owner" -> getMessage("legacy.kb_member.cannot_remove_owner", "Cannot remove the owner");
            case "Only owner or admin can perform this action" -> getMessage("legacy.kb_member.only_owner_or_admin", "Only owner or admin can perform this action");
            case "Only deleted items can be permanently deleted" -> getMessage("legacy.kb.only_deleted_can_be_permanently_deleted", "Only deleted items can be permanently deleted");
            case "cross-kb relation is not supported" -> getMessage("legacy.doc_relation.cross_kb_not_supported", "Cross-knowledge-base relation is not supported");
            case "relation not found" -> getMessage("legacy.doc_relation.not_found", "Relation not found");
            case "parentId cannot be self" -> getMessage("legacy.document.parent_self", "parentId cannot reference itself");
            case "parentId not in kb" -> getMessage("legacy.document.parent_not_in_kb", "parentId is not in the current knowledge base");
            case "密码错误" -> getMessage("legacy.document.password_incorrect", "Incorrect password");
            case "链接已过期" -> getMessage("legacy.storage.link_expired", "Link has expired");
            case "签名无效" -> getMessage("legacy.storage.invalid_signature", "Invalid signature");
            case "非法的存储键" -> getMessage("legacy.storage.invalid_key", "Invalid storage key");
            case "存储文件记录异常" -> getMessage("legacy.storage.record_error", "Storage file record error");
            case "上传文件失败: 写入存储后校验不存在" -> getMessage("legacy.storage.upload_verify_missing", "File upload failed: stored file could not be verified");
            case "未登录" -> getMessage("api.code.UNAUTHORIZED", "Unauthorized or session expired");
            case "该知识库非公开" -> getMessage("legacy.kb.not_public", "This knowledge base is not public");
            case "无权访问该文档" -> getMessage("legacy.document.no_permission", "You do not have permission to access this document");
            case "配置键已存在" -> getMessage("legacy.config.key_exists", "Configuration key already exists");
            case "默认配置不能禁用" -> getMessage("legacy.storage_config.default_cannot_disable", "Default configuration cannot be disabled");
            case "默认配置不能删除" -> getMessage("legacy.storage_config.default_cannot_delete", "Default configuration cannot be deleted");
            case "不能将未启用的配置设为默认" -> getMessage("legacy.storage_config.disabled_cannot_default", "Disabled configuration cannot be set as default");
            case "存储配置名称已存在" -> getMessage("legacy.storage_config.name_exists", "Storage configuration name already exists");
            case "权限编码已存在" -> getMessage("legacy.permission.code_exists", "Permission code already exists");
            case "字典编码已存在" -> getMessage("legacy.dict.code_exists", "Dictionary code already exists");
            case "系统内置字典不可删除" -> getMessage("legacy.dict.system_cannot_delete", "Built-in dictionary cannot be deleted");
            case "不允许创建 SUPER_ADMIN 角色，请使用 admin" -> getMessage("legacy.role.cannot_create_super_admin", "SUPER_ADMIN role cannot be created, please use admin");
            case "不允许设置 SUPER_ADMIN 角色，请使用 admin" -> getMessage("legacy.role.cannot_assign_super_admin", "SUPER_ADMIN role cannot be assigned, please use admin");
            case "Invalid token type: refresh token required" -> getMessage("legacy.auth.invalid_refresh_token_type", message);
            case "Invalid refresh token: username missing" -> getMessage("legacy.auth.refresh_username_missing", message);
            case "Invalid refresh token: missing jti/family" -> getMessage("legacy.auth.refresh_jti_missing", message);
            case "Refresh token not found" -> getMessage("legacy.auth.refresh_not_found", message);
            case "Refresh token reuse detected" -> getMessage("legacy.auth.refresh_reuse_detected", message);
            case "Refresh token family mismatch" -> getMessage("legacy.auth.refresh_family_mismatch", message);
            case "Refresh token expired" -> getMessage("legacy.auth.refresh_expired", message);
            case "Refresh token user mismatch" -> getMessage("legacy.auth.refresh_user_mismatch", message);
            default -> null;
        };
    }

    private String resolvePrefixedMessage(String message) {
        return resolvePrefix(message, "读取文件失败: ", "legacy.file.read_failed", "Failed to read file: {0}")
                .or(() -> resolvePrefix(message, "抓取URL失败: ", "legacy.asset.fetch_url_failed", "Failed to fetch URL: {0}"))
                .or(() -> resolvePrefix(message, "上传文件失败: ", "legacy.storage.upload_failed", "Failed to upload file: {0}"))
                .or(() -> resolvePrefix(message, "计算文件哈希失败: ", "legacy.storage.hash_failed", "Failed to calculate file hash: {0}"))
                .or(() -> resolvePrefix(message, "并发上传冲突处理失败: ", "legacy.storage.concurrent_conflict_failed", "Failed to handle concurrent upload conflict: {0}"))
                .or(() -> resolvePrefix(message, "创建本地存储目录失败: ", "legacy.storage.local_dir_create_failed", "Failed to create local storage directory: {0}"))
                .or(() -> resolvePrefix(message, "本地存储上传失败: ", "legacy.storage.local_upload_failed", "Local storage upload failed: {0}"))
                .or(() -> resolvePrefix(message, "本地存储删除失败: ", "legacy.storage.local_delete_failed", "Local storage delete failed: {0}"))
                .or(() -> resolvePrefix(message, "读取本地文件失败: ", "legacy.storage.local_read_failed", "Failed to read local file: {0}"))
                .or(() -> resolvePrefix(message, "S3 上传失败: ", "legacy.storage.s3_upload_failed", "S3 upload failed: {0}"))
                .or(() -> resolvePrefix(message, "S3 删除失败: ", "legacy.storage.s3_delete_failed", "S3 delete failed: {0}"))
                .or(() -> resolvePrefix(message, "S3 生成预签名URL失败: ", "legacy.storage.s3_presign_failed", "Failed to create S3 presigned URL: {0}"))
                .or(() -> resolvePrefix(message, "S3 下载失败: ", "legacy.storage.s3_download_failed", "S3 download failed: {0}"))
                .or(() -> resolvePrefix(message, "Failed to call AI service: ", "legacy.ai.call_failed", "Failed to call AI service: {0}"))
                .or(() -> resolvePrefix(message, "Failed to stream AI response: ", "legacy.ai.stream_failed", "Failed to stream AI response: {0}"))
                .or(() -> resolvePrefix(message, "AI API 响应异常: ", "legacy.ai.api_response_error", "AI API response error: {0}"))
                .or(() -> resolvePrefix(message, "AI 服务提示: ", "legacy.ai.service_tip", "AI service message: {0}"))
                .orElse(null);
    }

    private java.util.Optional<String> resolvePrefix(String message, String prefix, String key, String defaultPattern) {
        if (!message.startsWith(prefix)) {
            return java.util.Optional.empty();
        }
        String detail = message.substring(prefix.length()).trim();
        return java.util.Optional.of(getMessage(key, defaultPattern, detail));
    }

    private String getMessage(String key, String defaultMessage, Object... args) {
        Locale locale = resolveLocale();
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception ignored) {
            return MessageFormat.format(defaultMessage, args);
        }
    }

    private Locale resolveLocale() {
        String locale = LanguageContext.getLocale();
        if ("en-US".equalsIgnoreCase(locale)) {
            return Locale.US;
        }
        return Locale.SIMPLIFIED_CHINESE;
    }
}
