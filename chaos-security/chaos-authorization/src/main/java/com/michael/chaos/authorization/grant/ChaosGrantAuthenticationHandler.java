package com.michael.chaos.authorization.grant;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Map;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * 自定义 OAuth2 {@code grant_type} 登录的 SPI。
 *
 * <p>应用注册该类型的 Bean 后，可以扩展社交登录、二维码登录、硬件 token 登录等模式。
 * 处理器负责校验请求参数并返回 {@link LoginUser}；token 创建仍由授权模块统一完成。</p>
 */
public interface ChaosGrantAuthenticationHandler {

    /**
     * 返回当前实现处理的 OAuth2 grant type。
     */
    AuthorizationGrantType grantType();

    /**
     * 认证 token 端点请求。
     *
     * @param parameters 不包含客户端认证信息的 token 请求参数
     * @return 将作为 token principal 的已认证用户
     */
    LoginUser authenticate(Map<String, Object> parameters);
}
