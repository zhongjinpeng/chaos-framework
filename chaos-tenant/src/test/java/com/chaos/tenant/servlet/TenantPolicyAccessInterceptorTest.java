package com.chaos.tenant.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.core.context.RequestContext;
import com.chaos.tenant.access.RequireTenantAccess;
import com.chaos.tenant.access.TenantAccessAuthorizer;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class TenantPolicyAccessInterceptorTest {

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void methodPolicyShouldOverrideControllerPolicy() throws Exception {
        RequestContext.setTenantId("tenant-a");
        AtomicReference<String> evaluatedPolicy = new AtomicReference<>();
        TenantAccessAuthorizer authorizer = (policy, tenantId) -> {
            evaluatedPolicy.set(policy + ":" + tenantId);
            return true;
        };
        TenantPolicyAccessInterceptor interceptor = interceptor(authorizer, true);

        boolean allowed = interceptor.preHandle(
                request("GET"), new MockHttpServletResponse(), handler("shared"));

        assertThat(allowed).isTrue();
        assertThat(evaluatedPolicy).hasValue("SHARED:tenant-a");
    }

    @Test
    void rejectedPolicyShouldUseApplicationException() throws Exception {
        RequestContext.setTenantId("tenant-a");
        TenantPolicyAccessInterceptor interceptor = interceptor((policy, tenantId) -> false, true);

        assertThatThrownBy(() -> interceptor.preHandle(
                request("GET"), new MockHttpServletResponse(), handler("organization")))
                .isInstanceOf(TestAccessDeniedException.class)
                .hasMessage("ORGANIZATION:tenant-a");
    }

    @Test
    void observationModeShouldAllowRejectedPolicy() throws Exception {
        TenantPolicyAccessInterceptor interceptor = interceptor((policy, tenantId) -> false, false);

        boolean allowed = interceptor.preHandle(
                request("GET"), new MockHttpServletResponse(), handler("organization"));

        assertThat(allowed).isTrue();
    }

    @Test
    void optionsRequestShouldSkipPolicyEvaluation() throws Exception {
        AtomicReference<String> evaluatedPolicy = new AtomicReference<>();
        TenantPolicyAccessInterceptor interceptor = interceptor((policy, tenantId) -> {
            evaluatedPolicy.set(policy);
            return false;
        }, true);

        boolean allowed = interceptor.preHandle(
                request("OPTIONS"), new MockHttpServletResponse(), handler("organization"));

        assertThat(allowed).isTrue();
        assertThat(evaluatedPolicy.get()).isNull();
    }

    private static TenantPolicyAccessInterceptor interceptor(
            TenantAccessAuthorizer authorizer, boolean enforce) {
        return new TenantPolicyAccessInterceptor(
                authorizer,
                (policy, tenantId) -> new TestAccessDeniedException(policy + ":" + tenantId),
                enforce);
    }

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/test");
        request.setRequestURI("/test");
        return request;
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    @RequireTenantAccess("ORGANIZATION")
    private static final class TestController {

        void organization() {
        }

        @RequireTenantAccess("SHARED")
        void shared() {
        }
    }

    private static final class TestAccessDeniedException extends RuntimeException {

        private TestAccessDeniedException(String message) {
            super(message);
        }
    }
}
