package com.shop.api.common.error;

import com.shop.api.auth.RefreshTokenCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RefreshTokenCookieManager cookieManager;

    public GlobalExceptionHandler(RefreshTokenCookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode code = e.getErrorCode();
        if (code == ErrorCode.REFRESH_TOKEN_INVALID) {
            // 갱신 실패 시 클라이언트 쿠키를 삭제한다(계약 A2).
            return build(code, req, List.of(), cookieManager.clearCookie().toString());
        }
        return build(code, req, List.of(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field).reversed()
                        .thenComparing(FieldErrorDetail::message))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, req, errors, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest req) {
        return build(ErrorCode.VALIDATION_ERROR, req, List.of(), null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest req) {
        return build(ErrorCode.NOT_FOUND, req, List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                               HttpServletRequest req) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, req, List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), e);
        return build(ErrorCode.INTERNAL_ERROR, req, List.of(), null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, HttpServletRequest req,
                                                List<FieldErrorDetail> errors, String setCookie) {
        ResponseEntity.BodyBuilder b = ResponseEntity.status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON);
        if (setCookie != null) {
            b.header(HttpHeaders.SET_COOKIE, setCookie);
        }
        return b.body(ErrorResponse.of(code, req.getRequestURI(), errors));
    }
}
