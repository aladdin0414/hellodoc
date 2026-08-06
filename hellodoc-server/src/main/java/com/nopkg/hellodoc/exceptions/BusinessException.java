package com.nopkg.hellodoc.exceptions;

import com.nopkg.hellodoc.web.ApiResponse;

/**
 * Business logic exception with error code support.
 */
public class BusinessException extends RuntimeException {

    private final ApiResponse.Code code;

    public BusinessException(ApiResponse.Code code) {
        super(code.message());
        this.code = code;
    }

    public BusinessException(ApiResponse.Code code, String message) {
        super(message);
        this.code = code;
    }

    public ApiResponse.Code getCode() {
        return code;
    }
}
