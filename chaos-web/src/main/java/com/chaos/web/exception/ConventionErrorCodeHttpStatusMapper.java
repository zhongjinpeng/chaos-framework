package com.chaos.web.exception;

import java.util.function.Predicate;
import org.springframework.http.HttpStatus;

/**
 * 按错误码后缀约定映射 HTTP 状态。
 *
 * <p>只处理调用方指定的业务错误码前缀：{@code _NOT_FOUND} → 404、{@code _FORBIDDEN} → 403、
 * 重复与并发冲突 → 409，其余业务拒绝 → 400。返回 {@code null} 时由框架默认映射处理。</p>
 */
public final class ConventionErrorCodeHttpStatusMapper implements ErrorCodeHttpStatusMapper {

    private final Predicate<String> applies;

    /**
     * 创建约定映射器。
     *
     * @param applies 判断错误码是否属于本服务的业务码
     */
    public ConventionErrorCodeHttpStatusMapper(Predicate<String> applies) {
        this.applies = applies;
    }

    @Override
    public HttpStatus resolve(String code) {
        if (code == null || applies == null || !applies.test(code)) {
            return null;
        }
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("_FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.endsWith("_DUPLICATE")
                || code.contains("_DUPLICATE_")
                || code.endsWith("_VERSION_CONFLICT")
                || code.endsWith("_CONCURRENT_MODIFICATION")
                || code.endsWith("_CONSTRAINT_CONFLICT")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
