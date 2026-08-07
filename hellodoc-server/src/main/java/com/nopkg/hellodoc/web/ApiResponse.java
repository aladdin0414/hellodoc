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
        return new ApiResponse<>(Code.SUCCESS.code(), ApiResponseMessageBridge.resolveCodeMessage(Code.SUCCESS), data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(Code.SUCCESS.code(), ApiResponseMessageBridge.resolveCodeMessage(Code.SUCCESS), null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> success(Code code, T data) {
        return new ApiResponse<>(code.code(), ApiResponseMessageBridge.resolveCodeMessage(code), data);
    }

    public static <T> ApiResponse<T> error(Code code) {
        return new ApiResponse<>(code.code(), ApiResponseMessageBridge.resolveCodeMessage(code), null);
    }

    public static <T> ApiResponse<T> error(Code code, String message) {
        return new ApiResponse<>(code.code(), ApiResponseMessageBridge.resolveMessage(code, message), null);
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
        SUCCESS(0, "Success"),
        USERNAME_OR_PASSWORD_ERROR(1001, "Username or password error"),
        ACCOUNT_DISABLED(1002, "Account is disabled"),
        USERNAME_CONFLICT(1003, "Username already exists"),
        RESOURCE_NOT_FOUND(1004, "Resource not found"),
        TOKEN_INVALID(1005, "Invalid refreshToken"),
        TOKEN_TYPE_ERROR(1006, "Invalid token type"),
        PARAM_ERROR(1007, "Parameter error"),
        OLD_PASSWORD_WRONG(1008, "Old password incorrect"),
        INVALID_REQUEST(1009, "Invalid request"),
        UPLOAD_FILE_REQUIRED(1010, "Upload file required"),
        UPLOAD_IMAGE_ONLY(1011, "Images only"),
        UPLOAD_AVATAR_TOO_LARGE(1012, "Avatar image size exceeds 2MB limit"),
        UPLOAD_AVATAR_FAILED(1013, "Failed to upload avatar"),
        UNAUTHORIZED(401, "Unauthorized or session expired"),
        NO_PERMISSION(403, "No permission"),
        SYSTEM_ERROR(9999, "System error");

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
