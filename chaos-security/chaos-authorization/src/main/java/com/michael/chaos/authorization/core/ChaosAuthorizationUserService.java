package com.michael.chaos.authorization.core;

import com.michael.chaos.security.api.auth.LoginUser;

/**
 * 连接授权服务器与业务身份存储的扩展点。
 *
 * <p>启用 {@code chaos-authorization-starter} 的应用应提供该类型的 Bean。
 * 框架负责 OAuth2 token 签发；业务应用负责用户凭证校验、租户解析和权限加载。</p>
 */
public interface ChaosAuthorizationUserService {

    /**
     * 认证用户名密码登录请求。
     *
     * @param username 用户提交的用户名
     * @param password 用户提交的明文密码；实现方必须按自身密码策略完成校验
     * @return 包含租户、角色和权限信息的已认证用户
     */
    LoginUser authenticateByUsername(String username, String password);

    /**
     * 认证带租户标识的用户名密码登录请求。
     *
     * <p>租户标识由客户端通过 {@code tenant_id}(优先)或 {@code tenant_code} 参数提交,
     * 具体语义由实现方解释(租户 ID 或租户编码)。默认兼容旧实现,未覆盖该方法时忽略租户标识。</p>
     *
     * @param tenant 用户提交的租户标识(租户 ID 或租户编码)
     * @param username 用户提交的用户名
     * @param password 用户提交的明文密码；实现方必须按自身密码策略完成校验
     * @return 包含租户、角色和权限信息的已认证用户
     */
    default LoginUser authenticateByUsername(String tenant, String username, String password) {
        return authenticateByUsername(username, password);
    }

    /**
     * 在短信验证码通过校验后加载用户。
     *
     * @param mobile 用户提交的手机号
     * @return 包含租户、角色和权限信息的已认证用户
     */
    LoginUser loadByMobile(String mobile);
}
