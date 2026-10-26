package com.chaos.tenant.servlet;

import com.chaos.core.context.RequestContext;
import com.chaos.tenant.access.RequireTenantAccess;
import com.chaos.tenant.access.TenantAccessAuthorizer;
import com.chaos.tenant.access.TenantAccessDeniedHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces application-defined tenant access policies before invoking Spring MVC controllers.
 *
 * <p>The interceptor only checks the caller against the declared policy. Row visibility,
 * cross-tenant reads and write ownership remain application and persistence responsibilities.
 */
public final class TenantPolicyAccessInterceptor implements HandlerInterceptor {

    private static final System.Logger LOG =
            System.getLogger(TenantPolicyAccessInterceptor.class.getName());

    private final TenantAccessAuthorizer authorizer;

    private final TenantAccessDeniedHandler deniedHandler;

    private final boolean enforce;

    /**
     * Creates a tenant policy interceptor.
     *
     * @param authorizer application policy decision service
     * @param deniedHandler application-specific denial exception factory
     * @param enforce whether rejected policies throw; {@code false} records a warning and allows
     *        the request for migration observation
     */
    public TenantPolicyAccessInterceptor(
            TenantAccessAuthorizer authorizer,
            TenantAccessDeniedHandler deniedHandler,
            boolean enforce) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer must not be null");
        this.deniedHandler = Objects.requireNonNull(deniedHandler, "deniedHandler must not be null");
        this.enforce = enforce;
    }

    /**
     * Checks the method-level policy first and then the controller-level policy.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler resolved handler; non-controller handlers are allowed
     * @return {@code true} when processing may continue
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String policy = resolvePolicy(handlerMethod);
        if (policy == null) {
            return true;
        }

        String tenantId = RequestContext.tenantId();
        if (authorizer.isAllowed(policy, tenantId)) {
            return true;
        }

        LOG.log(System.Logger.Level.WARNING,
                "Tenant access policy rejected: method={0}, path={1}, policy={2}, tenantId={3}, userId={4}",
                request.getMethod(), request.getRequestURI(), policy, tenantId, RequestContext.userId());
        if (enforce) {
            throw Objects.requireNonNull(
                    deniedHandler.accessDenied(policy, tenantId),
                    "deniedHandler must return an exception");
        }
        return true;
    }

    private static String resolvePolicy(HandlerMethod handlerMethod) {
        RequireTenantAccess methodAccess = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequireTenantAccess.class);
        if (methodAccess != null) {
            return requiredPolicy(methodAccess);
        }
        RequireTenantAccess typeAccess = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequireTenantAccess.class);
        return typeAccess == null ? null : requiredPolicy(typeAccess);
    }

    private static String requiredPolicy(RequireTenantAccess access) {
        String policy = access.value();
        if (policy == null || policy.isBlank()) {
            throw new IllegalStateException("@RequireTenantAccess policy must not be blank");
        }
        return policy;
    }
}
