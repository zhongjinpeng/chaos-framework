package com.michael.chaos.tenant;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 租户套餐信息。
 *
 * @param code 套餐编码
 * @param name 套餐名称
 * @param expiresAt 套餐到期时间
 * @param quotas 套餐配额，key 为配额编码，value 为配额值
 */
public record TenantPlan(
        String code,
        String name,
        Instant expiresAt,
        Map<String, Long> quotas
) {

    /**
     * 规范化套餐字段，避免下游治理逻辑处理空指针。
     */
    public TenantPlan {
        code = Objects.requireNonNullElse(code, "").trim();
        name = Objects.requireNonNullElse(name, "").trim();
        quotas = quotas == null ? Map.of() : Map.copyOf(quotas);
    }

    /**
     * 返回空套餐。
     */
    public static TenantPlan empty() {
        return new TenantPlan("", "", null, Map.of());
    }
}
