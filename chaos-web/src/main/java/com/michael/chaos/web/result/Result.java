package com.michael.chaos.web.result;

import com.michael.chaos.domain.dto.PageResult;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.exception.ErrorCode;
import com.michael.chaos.trace.TraceContext;
import java.time.Instant;
import java.util.List;

/**
 * 统一 API 响应模型。
 *
 * @param code 业务错误码，成功时为 {@code 0}
 * @param message 响应消息
 * @param data 响应数据
 * @param traceId 当前请求 traceId
 * @param timestamp 响应生成时间戳，单位毫秒
 * @param <T> 响应数据类型
 */
public record Result<T>(
        String code,
        String message,
        T data,
        String traceId,
        long timestamp
) {

    /**
     * 创建成功响应并携带数据。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return of(CommonErrorCode.SUCCESS, data, CommonErrorCode.SUCCESS.message());
    }

    /**
     * 创建分页查询成功响应。
     *
     * @param page 分页查询结果
     * @param <T> 记录类型
     * @return 分页成功响应
     */
    public static <T> Result<PageResult<T>> page(PageResult<T> page) {
        return success(page);
    }

    /**
     * 创建分页查询成功响应。
     *
     * @param records 当前页数据
     * @param total 总记录数
     * @param pageNo 当前页码
     * @param pageSize 每页记录数
     * @param <T> 记录类型
     * @return 分页成功响应
     */
    public static <T> Result<PageResult<T>> page(List<T> records, long total, long pageNo, long pageSize) {
        return page(new PageResult<>(records, total, pageNo, pageSize));
    }

    /**
     * 创建不携带数据的成功响应。
     *
     * @return 成功响应
     */
    public static Result<Void> success() {
        return of(CommonErrorCode.SUCCESS, null, CommonErrorCode.SUCCESS.message());
    }

    /**
     * 使用错误码默认消息创建失败响应。
     *
     * @param errorCode 错误码
     * @return 失败响应
     */
    public static Result<Void> failure(ErrorCode errorCode) {
        return of(errorCode, null, errorCode.message());
    }

    /**
     * 使用自定义消息创建失败响应。
     *
     * @param errorCode 错误码
     * @param message 展示消息
     * @return 失败响应
     */
    public static Result<Void> failure(ErrorCode errorCode, String message) {
        return of(errorCode, null, message);
    }

    /**
     * 按指定错误码、数据和消息创建响应。
     *
     * @param errorCode 错误码
     * @param data 响应数据
     * @param message 展示消息
     * @param <T> 响应数据类型
     * @return 统一响应
     */
    public static <T> Result<T> of(ErrorCode errorCode, T data, String message) {
        return new Result<>(
                errorCode.code(),
                message,
                data,
                TraceContext.traceId(),
                Instant.now().toEpochMilli()
        );
    }
}
