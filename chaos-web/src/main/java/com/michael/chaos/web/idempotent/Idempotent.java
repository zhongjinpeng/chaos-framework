package com.michael.chaos.web.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 请求或方法幂等注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 固定幂等 key；为空时由适配层从请求边界（例如 {@code Idempotency-Key} 请求头）读取。
     *
     * <p>固定 key 仍会按租户、用户、方法和路径隔离，不会成为跨用户的全局锁。</p>
     */
    String key() default "";

    /**
     * 缺少幂等 key 时是否拒绝请求。
     *
     * <p>默认 {@code true}：标注了幂等却拿不到 key 时直接返回 400，避免调用方误以为已受保护。
     * 设为 {@code false} 时缺少 key 的请求会跳过幂等校验直接放行。</p>
     */
    boolean requireKey() default true;

    /**
     * 重复请求是否回放首次响应。
     *
     * <p>仅在 {@code chaos.web.idempotent.replay.enabled=true} 时有意义：开启后同一个幂等 key 的重复请求
     * 直接返回首次执行的状态码与响应体，并带上 {@code Idempotency-Replayed: true} 响应头；
     * 关闭后重复请求仍然返回 409。</p>
     *
     * <p>响应体很大、或响应中包含一次性内容（验证码图片、预签名 URL、下载流）的接口应设为 {@code false}：
     * 回放这类响应对调用方没有意义，还会白白占用存储。</p>
     */
    boolean replay() default true;
}
