package com.michael.chaos.web.exception;

import com.michael.chaos.core.exception.ChaosException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.exception.ErrorCode;
import com.michael.chaos.web.i18n.ChaosMessageKeys;
import com.michael.chaos.web.i18n.ErrorMessageResolver;
import com.michael.chaos.web.i18n.MessageSourceErrorMessageResolver;
import com.michael.chaos.web.result.Result;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>负责将框架异常、参数校验异常和未知异常统一转换为 {@link Result}，保持 Web 层响应结构一致。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>Spring 自带的 {@link ErrorResponse} 异常（{@code ResponseStatusException}、{@code NoResourceFoundException}、
 *     {@code MaxUploadSizeExceededException} 等）沿用异常自身的 HTTP 状态码，不再一律变成 500，
 *     避免扫描器请求刷出大量 error 日志。</li>
 *     <li>Spring Security 的 {@code AccessDeniedException} / {@code AuthenticationException} 原样抛出，
 *     交给 {@code ExceptionTranslationFilter} 按认证状态返回 401 或 403，而不是被兜底成 500。</li>
 *     <li>5xx 的 {@link ChaosException} 记录 error 日志并保留堆栈，但响应只返回错误码默认消息，
 *     避免把数据库、下游服务等内部信息泄露给调用方。</li>
 *     <li>所有对外文案统一经过 {@link ErrorMessageResolver} 解析。此前错误码默认消息是英文、而这里的参数校验
 *     提示是硬编码中文，同一个服务的错误响应会中英混杂，且客户端语言完全无法影响文案；
 *     统一解析后 {@code Accept-Language} 对框架内置提示同样生效。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 需要交还给 Spring Security 过滤器处理的异常类型（按类名匹配，避免强依赖 spring-security）。
     */
    private static final Set<String> SECURITY_EXCEPTION_TYPES = Set.of(
            "org.springframework.security.access.AccessDeniedException",
            "org.springframework.security.core.AuthenticationException"
    );

    /**
     * 多条字段校验提示之间的分隔符。
     */
    private static final String VALIDATION_SEPARATOR = "; ";

    private final ErrorCodeHttpStatusMapper statusMapper;

    private final ErrorMessageResolver messageResolver;

    /**
     * 创建全局异常处理器，使用框架内置文案资源包。
     */
    public GlobalExceptionHandler() {
        this(null, null);
    }

    /**
     * 创建全局异常处理器并指定错误码到 HTTP 状态码的映射。
     */
    public GlobalExceptionHandler(ErrorCodeHttpStatusMapper statusMapper) {
        this(statusMapper, null);
    }

    /**
     * 创建全局异常处理器。
     *
     * @param statusMapper 错误码到 HTTP 状态码的映射，可为 {@code null}
     * @param messageResolver 文案解析器；为 {@code null} 时只使用框架内置资源包，
     *        这样脱离 Spring 容器直接 new 出来的处理器（例如 MockMvc 单元测试）文案行为与运行时一致
     */
    public GlobalExceptionHandler(ErrorCodeHttpStatusMapper statusMapper, ErrorMessageResolver messageResolver) {
        this.statusMapper = statusMapper;
        this.messageResolver = messageResolver != null
                ? messageResolver
                : new MessageSourceErrorMessageResolver(() -> null, null);
    }

    /**
     * 处理框架统一异常。
     */
    @ExceptionHandler(ChaosException.class)
    public ResponseEntity<Result<Void>> handleChaosException(ChaosException ex) {
        HttpStatus status = resolveStatus(ex.errorCode().code());
        if (status.is5xxServerError()) {
            log.error("Server error: code={}", ex.errorCode().code(), ex);
            return ResponseEntity.status(status).body(failure(ex.errorCode()));
        }
        log.debug("Business rejected: code={}, message={}", ex.errorCode().code(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(Result.failure(ex.errorCode(), messageResolver.resolve(ex.errorCode(), ex.getMessage())));
    }

    /**
     * 处理 {@code @RequestBody} 参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(badRequest(fieldErrorMessage(ex.getBindingResult().getFieldErrors())));
    }

    /**
     * 处理表单和查询参数绑定异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        return ResponseEntity.badRequest().body(badRequest(fieldErrorMessage(ex.getBindingResult().getFieldErrors())));
    }

    /**
     * 处理方法参数约束校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> validationField(String.valueOf(violation.getPropertyPath()), violation.getMessage()))
                .collect(Collectors.joining(VALIDATION_SEPARATOR));
        return ResponseEntity.badRequest().body(badRequest(message));
    }

    /**
     * 处理 @RequestParam 缺失:必填查询参数未提供。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = messageResolver.resolve(ChaosMessageKeys.MISSING_PARAMETER,
                "Missing required parameter: " + ex.getParameterName(), ex.getParameterName());
        return ResponseEntity.badRequest().body(badRequest(message));
    }

    /**
     * 处理 @RequestHeader 缺失。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = messageResolver.resolve(ChaosMessageKeys.MISSING_HEADER,
                "Missing required request header: " + ex.getHeaderName(), ex.getHeaderName());
        return ResponseEntity.badRequest().body(badRequest(message));
    }

    /**
     * 处理参数类型转换失败(如把字符串当 Integer)。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "<unknown>";
        String message = messageResolver.resolve(ChaosMessageKeys.TYPE_MISMATCH,
                "Parameter " + ex.getName() + " has an invalid type, expected " + expected, ex.getName(), expected);
        return ResponseEntity.badRequest().body(badRequest(message));
    }

    /**
     * 处理请求体解析失败(JSON 格式错误、字段类型错误等)。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body parse failed: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .badRequest()
                .body(badRequest(messageResolver.resolve(ChaosMessageKeys.MALFORMED_BODY, "Malformed request body")));
    }

    /**
     * 处理不支持的 HTTP 方法。错误码与 405 状态码保持一致。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = messageResolver.resolve(ChaosMessageKeys.METHOD_NOT_SUPPORTED,
                "Request method not supported: " + ex.getMethod(), ex.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.failure(CommonErrorCode.METHOD_NOT_ALLOWED, message));
    }

    /**
     * 处理不支持的 Content-Type。错误码与 415 状态码保持一致。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        String contentType = ex.getContentType() != null ? ex.getContentType().toString() : "<unknown>";
        String message = messageResolver.resolve(ChaosMessageKeys.MEDIA_TYPE_NOT_SUPPORTED,
                "Content-Type not supported: " + contentType, contentType);
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.failure(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, message));
    }

    /**
     * 处理静态资源或路由不存在。
     *
     * <p>扫描器会大量请求不存在的路径，只记录 debug 日志。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(failure(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 处理未被业务显式捕获的异常。
     *
     * @throws Exception Spring Security 异常原样抛出，交由安全过滤器链处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) throws Exception {
        if (isSecurityException(ex)) {
            throw ex;
        }
        if (ex instanceof ErrorResponse errorResponse) {
            return handleErrorResponse(ex, errorResponse.getStatusCode());
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(failure(CommonErrorCode.INTERNAL_ERROR));
    }

    /**
     * 按 Spring {@link ErrorResponse} 自带的状态码返回，4xx 不打 error 日志且不回显异常原文。
     */
    private ResponseEntity<Result<Void>> handleErrorResponse(Exception ex, HttpStatusCode statusCode) {
        ErrorCode errorCode = errorCodeFor(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Framework server error: status={}", statusCode.value(), ex);
        } else {
            log.debug("Framework client error: status={}, exception={}", statusCode.value(), ex.toString());
        }
        return ResponseEntity.status(statusCode).body(failure(errorCode));
    }

    private static ErrorCode errorCodeFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> CommonErrorCode.BAD_REQUEST;
            case 401 -> CommonErrorCode.UNAUTHORIZED;
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            default -> statusCode.is5xxServerError() ? CommonErrorCode.INTERNAL_ERROR : new HttpStatusErrorCode(statusCode);
        };
    }

    /**
     * 构造只带错误码默认文案的失败响应。
     */
    private Result<Void> failure(ErrorCode errorCode) {
        return Result.failure(errorCode, messageResolver.resolve(errorCode));
    }

    /**
     * 构造 400 失败响应。
     */
    private Result<Void> badRequest(String message) {
        return Result.failure(CommonErrorCode.BAD_REQUEST, message);
    }

    /**
     * 把字段校验错误拼成单行提示。
     */
    private String fieldErrorMessage(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> validationField(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(VALIDATION_SEPARATOR));
    }

    /**
     * 单条字段提示的拼接方式也走消息资源，便于按语言调整"字段名 + 校验消息"的排列。
     *
     * <p>字段级校验消息本身由 Bean Validation 产生，已经支持 {@code ValidationMessages.properties} 国际化，
     * 这里不重复翻译，只负责拼接格式。</p>
     */
    private String validationField(String field, String message) {
        String text = message == null ? "" : message;
        return messageResolver.resolve(ChaosMessageKeys.VALIDATION_FIELD, field + " " + text, field, text);
    }

    private static boolean isSecurityException(Throwable ex) {
        for (Class<?> type = ex.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (SECURITY_EXCEPTION_TYPES.contains(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将业务错误码映射为 HTTP 状态码。
     */
    private HttpStatus resolveStatus(String code) {
        if (statusMapper != null) {
            HttpStatus mapped = statusMapper.resolve(code);
            if (mapped != null) {
                return mapped;
            }
        }
        return switch (code) {
            case "400" -> HttpStatus.BAD_REQUEST;
            case "401" -> HttpStatus.UNAUTHORIZED;
            case "403" -> HttpStatus.FORBIDDEN;
            case "404" -> HttpStatus.NOT_FOUND;
            case "405" -> HttpStatus.METHOD_NOT_ALLOWED;
            case "409" -> HttpStatus.CONFLICT;
            case "415" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case "429" -> HttpStatus.TOO_MANY_REQUESTS;
            case "503" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 未内置的 4xx 状态码直接以状态码数字作为错误码。
     */
    private record HttpStatusErrorCode(HttpStatusCode statusCode) implements ErrorCode {

        @Override
        public String code() {
            return String.valueOf(statusCode.value());
        }

        @Override
        public String message() {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            return status == null ? "error" : status.getReasonPhrase().toLowerCase();
        }
    }
}
