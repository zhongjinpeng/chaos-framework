package com.michael.chaos.security.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Map;

/**
 * 主体属性解析器。
 *
 * <p>ABAC 条件里的 {@code subject.<name>} 属性来自这里，例如部门、职级、数据归属地。
 * 实现方负责缓存，避免每次鉴权都回源。</p>
 */
@FunctionalInterface
public interface SubjectAttributeResolver {

    /**
     * 解析主体属性。
     */
    Map<String, Object> resolve(LoginUser user);
}
