package com.nopkg.hellodoc.web.handler;

import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ BadCredentialsException.class, UsernameNotFoundException.class })
    public ApiResponse<Void> handleAuthenticationException(Exception e) {
        return ApiResponse.error(ApiResponse.Code.USERNAME_OR_PASSWORD_ERROR);
    }

    @ExceptionHandler(DisabledException.class)
    public ApiResponse<Void> handleDisabledException(DisabledException e) {
        return ApiResponse.error(ApiResponse.Code.ACCOUNT_DISABLED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        return ApiResponse.error(ApiResponse.Code.NO_PERMISSION);
    }

    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        HttpStatus status = HttpStatus.OK;
        if (e.getCode() == ApiResponse.Code.NO_PERMISSION) {
            status = HttpStatus.FORBIDDEN;
        } else if (e.getCode() == ApiResponse.Code.UNAUTHORIZED) {
            status = HttpStatus.UNAUTHORIZED;
        }
        return org.springframework.http.ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ApiResponse.error(ApiResponse.Code.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(org.springframework.web.bind.MethodArgumentNotValidException e) {
        log.warn("Validation error: {}", e.getMessage());
        String msg = null;
        if (e.getBindingResult() != null && e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (!StringUtils.hasText(msg)) {
            return ApiResponse.error(ApiResponse.Code.PARAM_ERROR);
        }
        return ApiResponse.error(ApiResponse.Code.PARAM_ERROR, msg);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        // Log full stack trace for internal debugging
        log.error("Unexpected error occurred", e);
        return ApiResponse.error(ApiResponse.Code.SYSTEM_ERROR);
    }
}
