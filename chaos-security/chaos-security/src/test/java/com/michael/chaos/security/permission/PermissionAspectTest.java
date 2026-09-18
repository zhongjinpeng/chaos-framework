package com.michael.chaos.security.permission;

import static com.michael.chaos.security.support.AspectTestSupport.joinPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.test.audit.CapturingAuditEventPublisher;
import com.michael.chaos.security.annotation.Permission;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
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


    private static final class SecuredService {

        @Permission("order:create")
        String createOrder() {
            return "ok";
        }
    }
}
