package com.foodie.common;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p>原则：
 * <ul>
 *   <li>客户端错误（4xx）用 WARN 记一行，不打堆栈，避免日志被刷爆；</li>
 *   <li>服务端错误（5xx）用 ERROR 打全堆栈，便于排查；</li>
 *   <li>任何情况下都不把堆栈或 SQL 细节返回给前端，
 *       只返回统一结构和 traceId。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：错误码与语义由抛出方决定。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.errorCode();
        log.warn("业务异常 [{}] {} {} -> {}", errorCode.code(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(toHttpStatus(errorCode))
                .body(Result.fail(errorCode, ex.getMessage()));
    }

    /** @Valid 校验请求体失败：把字段级错误拼成一句可读提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleBodyValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("；"));
        if (detail.isEmpty()) {
            detail = ErrorCode.BAD_REQUEST.defaultMessage();
        }
        log.warn("参数校验失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), detail);
        return badRequest(detail);
    }

    /** 查询参数/路径参数上的 @Validated 校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleParamValidation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), detail);
        return badRequest(detail.isEmpty() ? ErrorCode.BAD_REQUEST.defaultMessage() : detail);
    }

    /** 请求体不是合法 JSON，或字段类型对不上。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("请求体解析失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return badRequest("请求体格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        log.warn("缺少必填参数 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getParameterName());
        return badRequest("缺少必填参数：" + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest request) {
        log.warn("参数类型不匹配 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getName());
        return badRequest("参数格式不正确：" + ex.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                             HttpServletRequest request) {
        log.warn("上传超限 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.fail(ErrorCode.PAYLOAD_TOO_LARGE, "上传文件过大，单张图片请控制在 5MB 以内"));
    }

    /** @PreAuthorize 在 Controller 层抛出的拒绝访问。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("拒绝访问 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage()));
    }

    /** 认证异常：理论上已被 Security 过滤器拦截，这里兜底。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("认证失败 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest request) {
        log.warn("方法不支持 {} {} -> {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ErrorCode.BAD_REQUEST, "该接口不支持 " + ex.getMethod() + " 方法"));
    }

    /** 静态资源/未知路径。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage()));
    }

    /** 兜底：任何未预期异常都不外泄细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage()));
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private HttpStatus toHttpStatus(ErrorCode errorCode) {
        HttpStatus status = HttpStatus.resolve(errorCode.code());
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
