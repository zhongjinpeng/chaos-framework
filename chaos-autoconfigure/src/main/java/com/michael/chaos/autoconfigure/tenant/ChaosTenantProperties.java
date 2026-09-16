package com.michael.chaos.autoconfigure.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 租户治理配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.tenant")
public class ChaosTenantProperties {

    /**
     * 是否启用租户治理自动装配。
     */
    private boolean enabled = true;

    /**
     * 租户状态未知、缺失或不可访问时是否拒绝请求。
     */
    private boolean failClosed = true;

    /**
     * Servlet 服务端租户状态校验配置。
     */
    @Valid
    @NotNull(message = "chaos.tenant.servlet-filter must not be null")
    private ServletFilter servletFilter = new ServletFilter();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public ServletFilter getServletFilter() {
        return servletFilter;
    }

    public void setServletFilter(ServletFilter servletFilter) {
        this.servletFilter = servletFilter == null ? new ServletFilter() : servletFilter;
    }

    /**
     * Servlet 租户状态校验过滤器配置。
     */
    public static class ServletFilter {

        /**
         * 是否在 Servlet 服务中启用租户状态校验过滤器。
         *
         * <p>默认关闭以保持兼容；启用前需要接入真实的 TenantStatusProvider，并确认租户 ID 来自认证结果。</p>
         */
        private boolean enabled = false;

        /**
         * 不校验租户的路径（Ant 风格），例如 {@code /actuator/**}、登录接口。
         */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths == null ? new ArrayList<>() : excludePaths;
        }
    }
}
