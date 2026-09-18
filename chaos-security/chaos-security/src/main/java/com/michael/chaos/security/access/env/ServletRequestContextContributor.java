package com.michael.chaos.security.access.env;

import com.michael.chaos.security.api.access.AccessEnvironment;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 请求环境属性贡献者。
 *
 * <p>写入 {@code http.method}、{@code http.path}，并在 MDC 未提供 {@code clientIp} 时回退到直连地址。</p>
 */
public class ServletRequestContextContributor implements AuthorizationContextContributor {

    @Override
    public void contribute(Map<String, Object> environment) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        environment.put(AccessEnvironment.HTTP_METHOD, request.getMethod());
        environment.put(AccessEnvironment.HTTP_PATH, request.getRequestURI());
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            environment.putIfAbsent(AccessEnvironment.CLIENT_IP, remoteAddress);
        }
    }
}
