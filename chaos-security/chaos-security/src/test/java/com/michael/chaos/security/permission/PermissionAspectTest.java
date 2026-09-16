package com.michael.chaos.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.security.annotation.Permission;
import com.michael.chaos.security.api.auth.LoginUser;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 方法权限审计测试。
 */
class PermissionAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 权限拒绝时应发布审计事件。
     */
    @Test
    void shouldPublishAuditEventWhenPermissionDenied() throws Throwable {
        CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();
        PermissionCheckService permissionCheckService = (user, permission) -> false;
        PermissionAspect aspect = new PermissionAspect(permissionCheckService, publisher);
        LoginUser loginUser = new LoginUser("1001", "alice", "tenant-a", Set.of(), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(loginUser, null, "ROLE_USER"));
        ProceedingJoinPoint joinPoint = joinPoint(SecuredService.class.getDeclaredMethod("createOrder"));

        assertThatThrownBy(() -> aspect.checkPermission(joinPoint))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(publisher.events()).hasSize(1);
        AuditEvent event = publisher.events().getFirst();
        assertThat(event.action()).isEqualTo("security.permission.denied");
        assertThat(event.principalId()).isEqualTo("1001");
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.attributes()).containsEntry("permission", "order:create");
    }

    private ProceedingJoinPoint joinPoint(Method method) throws Throwable {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[]{MethodSignature.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getDeclaringType" -> method.getDeclaringClass();
                    case "getName" -> method.getName();
                    default -> defaultValue(invokedMethod.getReturnType());
                }
        );
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getSignature" -> signature;
                    case "proceed" -> "ok";
                    default -> defaultValue(invokedMethod.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class
                || type == float.class || type == double.class || type == char.class) {
            return 0;
        }
        return null;
    }

    private static final class CapturingAuditEventPublisher implements AuditEventPublisher {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void publish(AuditEvent event) {
            events.add(event);
        }

        private List<AuditEvent> events() {
            return events;
        }
    }

    private static final class SecuredService {

        @Permission("order:create")
        String createOrder() {
            return "ok";
        }
    }
}
