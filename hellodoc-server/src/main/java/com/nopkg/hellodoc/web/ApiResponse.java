package com.nopkg.hellodoc.web;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Code.SUCCESS.code(), Code.SUCCESS.message(), data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(Code.SUCCESS.code(), Code.SUCCESS.message(), null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> success(Code code, T data) {
        return new ApiResponse<>(code.code(), code.message(), data);
    }

    public static <T> ApiResponse<T> error(Code code) {
        return new ApiResponse<>(code.code(), code.message(), null);
    }

    public static <T> ApiResponse<T> error(Code code, String message) {
        return new ApiResponse<>(code.code(), message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public enum Code {
        SUCCESS(0, "成功"),
        USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
        ACCOUNT_DISABLED(1002, "账号已被禁用"),
        USERNAME_CONFLICT(1003, "用户名已存在"),
        RESOURCE_NOT_FOUND(1004, "资源不存在"),
        TOKEN_INVALID(1005, "refreshToken无效"),
        TOKEN_TYPE_ERROR(1006, "令牌类型错误"),
        PARAM_ERROR(1007, "参数错误"),
        OLD_PASSWORD_WRONG(1008, "旧密码错误"),
        INVALID_REQUEST(1009, "参数无效"),
        UPLOAD_FILE_REQUIRED(1010, "请选择要上传的文件"),
        UPLOAD_IMAGE_ONLY(1011, "只能上传图片文件"),
        UPLOAD_AVATAR_TOO_LARGE(1012, "图片大小不能超过2MB"),
        UPLOAD_AVATAR_FAILED(1013, "头像上传失败"),
        UNAUTHORIZED(401, "未登录或登录超时"),
        NO_PERMISSION(403, "没有权限访问"),
        SYSTEM_ERROR(9999, "系统异常");

        private final int code;
        private final String message;

        Code(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int code() {
            return code;
        }

        public String message() {
            return message;
        }
    }
}
