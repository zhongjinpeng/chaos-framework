package com.michael.chaos.core.constant;

import java.util.List;

/**
 * 默认被视为生产环境的 Spring profile。
 *
 * <p>全局生产安全检查（{@code chaos.production-safety.profiles}）与授权服务器生产安全检查
 * （{@code chaos.authorization.production-safety.profiles}）必须使用同一份默认值：
 * 两处曾经不一致（后者缺少 {@code prd}），导致以 {@code prd} 启动的授权服务器绕过了默认密钥、临时 JWK 等检查。</p>
 */
public final class ProductionProfiles {

    /**
     * 默认生产 profile 列表。
     */
    public static final List<String> DEFAULTS = List.of("prod", "production", "prd");

    private ProductionProfiles() {
    }

    /**
     * 以数组形式返回默认生产 profile，每次返回新数组，调用方可以安全修改。
     */
    public static String[] defaultsArray() {
        return DEFAULTS.toArray(String[]::new);
    }
}
