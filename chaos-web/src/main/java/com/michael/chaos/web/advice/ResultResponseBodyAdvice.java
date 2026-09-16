package com.michael.chaos.web.advice;

import com.michael.chaos.web.annotation.IgnoreResponseWrap;
import com.michael.chaos.web.result.Result;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Controller 响应统一包装增强。
 *
 * <p>未显式返回 {@link Result} 且未标注 {@link IgnoreResponseWrap} 的 JSON 接口会被包装为统一响应。</p>
 *
 * <p>包装边界（均为修复原实现问题而设）：</p>
 * <ul>
 *     <li>只在选中的转换器是 Jackson 时包装。原实现只看方法声明类型，{@code ResponseEntity<String>}、
 *     返回 {@code Object} 的字符串或 {@code byte[]} 已选中 String/ByteArray 转换器，
 *     被替换成 Result 后会抛 ClassCastException。</li>
 *     <li>排除 Spring Boot {@code ErrorController}、Actuator 和 springdoc。原实现把 {@code /error} 的
 *     {@code ResponseEntity<Map>} 也包装为 {@code code=0, message=success}，
 *     过滤器阶段的 404/500 因此看起来像成功响应。</li>
 * </ul>
 */
@RestControllerAdvice
public class ResultResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 不参与包装的框架包名前缀。
     */
    private static final List<String> EXCLUDED_PACKAGE_PREFIXES = List.of(
            "org.springframework.boot.autoconfigure.web.servlet.error.",
            "org.springframework.boot.actuate.",
            "org.springdoc."
    );

    private static final String ERROR_CONTROLLER_CLASS = "org.springframework.boot.web.servlet.error.ErrorController";

    /**
     * 判断当前响应是否需要统一包装。
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        Class<?> parameterType = returnType.getParameterType();
        Class<?> containingClass = returnType.getContainingClass();
        return !Result.class.isAssignableFrom(parameterType)
                && !CharSequence.class.isAssignableFrom(parameterType)
                && !Resource.class.isAssignableFrom(parameterType)
                && !StreamingResponseBody.class.isAssignableFrom(parameterType)
                && !returnType.hasMethodAnnotation(IgnoreResponseWrap.class)
                && !containingClass.isAnnotationPresent(IgnoreResponseWrap.class)
                && !isExcludedFrameworkController(containingClass);
    }

    /**
     * 将 Controller 返回值包装为 {@link Result#success(Object)}。
     * 若 body 在运行时已是 {@link Result}(如 ExceptionHandler 经 {@code ResponseEntity<Result<?>>} 返回),
     * 直接返回避免双重包装。
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Result || body instanceof CharSequence || body instanceof byte[]) {
            return body;
        }
        return Result.success(body);
    }

    private static boolean isExcludedFrameworkController(Class<?> containingClass) {
        String className = containingClass.getName();
        if (EXCLUDED_PACKAGE_PREFIXES.stream().anyMatch(className::startsWith)) {
            return true;
        }
        for (Class<?> type = containingClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Class<?> implemented : type.getInterfaces()) {
                if (ERROR_CONTROLLER_CLASS.equals(implemented.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
