package com.michael.chaos.security.access;

import static com.michael.chaos.security.support.AspectTestSupport.joinPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.test.audit.CapturingAuditEventPublisher;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.annotation.AccessAttribute;
import com.michael.chaos.security.annotation.RequireAccess;
import com.michael.chaos.security.api.access.AccessPolicyFactory;
import com.michael.chaos.security.api.access.AttributeOperator;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.ConditionDefinition;
import com.michael.chaos.security.api.access.DefaultAuthorizationManager;
import com.michael.chaos.security.api.access.PolicyDefinition;
import com.michael.chaos.security.api.access.RbacAuthorizationPolicy;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.permission.PermissionDeniedException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 统一授权切面测试。
 */
class RequireAccessAspectTest {

    private final CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 只能改自己的订单：命中 ABAC 条件时放行。
     */
    @Test
    void shouldAllowWhenAbacConditionMatches() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("order:update")));
        RequireAccessAspect aspect = aspect(ownerOnlyPolicy());

        Object result = aspect.checkAccess(joinPoint(updateMethod(), new Order("o-1", "1001")));

        assertThat(result).isEqualTo("ok");
        assertThat(publisher.events()).isEmpty();
    }

    /**
     * 资源属性不匹配时应拒绝，并发布审计事件。
     */
    @Test
    void shouldDenyAndAuditWhenAbacConditionFails() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("order:update")));
        RequireAccessAspect aspect = aspect(ownerOnlyPolicy());
        ProceedingJoinPoint joinPoint = joinPoint(updateMethod(), new Order("o-2", "2002"));

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint)).isInstanceOf(PermissionDeniedException.class);

        assertThat(publisher.events()).hasSize(1);
        AuditEvent event = publisher.events().getFirst();
        assertThat(event.action()).isEqualTo("security.permission.denied");
        assertThat(event.principalId()).isEqualTo("1001");
        assertThat(event.attributes())
                .containsEntry("action", "order:update")
                .containsEntry("resourceType", "order")
                .containsEntry("resourceId", "o-2");
    }

    /**
     * RBAC 拒绝优先于 ABAC 放行：没有权限编码时同样拒绝。
     */
    @Test
    void shouldDenyWhenSubjectHasNoPermission() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of()));
        RequireAccessAspect aspect = aspect();
        ProceedingJoinPoint joinPoint = joinPoint(updateMethod(), new Order("o-1", "1001"));

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint)).isInstanceOf(PermissionDeniedException.class);
    }

    /**
     * 未登录时应拒绝。
     */
    @Test
    void shouldDenyAnonymousSubject() throws Throwable {
        RequireAccessAspect aspect = aspect();
        ProceedingJoinPoint joinPoint = joinPoint(updateMethod(), new Order("o-1", "1001"));

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint)).isInstanceOf(PermissionDeniedException.class);
    }

    /**
     * 环境属性贡献者产出的属性应参与决策。
     */
    @Test
    void shouldApplyEnvironmentContributors() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("order:update")));
        AuthorizationPolicy officeHours = AccessPolicyFactory.create(
                PolicyDefinition.deny(
                        "deny-outside-office-hours",
                        Set.of("order:update"),
                        List.of(ConditionDefinition.of("environment.hour", AttributeOperator.GT, Set.of("18")))),
                "test");
        AuthorizationContextContributor contributor = environment -> environment.put("hour", 22);
        RequireAccessAspect aspect = new RequireAccessAspect(
                new DefaultAuthorizationManager(List.of(new RbacAuthorizationPolicy(), officeHours)),
                new AccessSubjectFactory(null),
                new AccessExpressionEvaluator(),
                contributor,
                publisher,
                null);

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint(updateMethod(), new Order("o-1", "1001"))))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(publisher.events().getFirst().attributes())
                .containsEntry("policy", "deny-outside-office-hours");
        assertThat(publisher.events().getFirst().reason()).isNotBlank();
    }

    /**
     * 自定义权限解析器补全的权限应生效。
     */
    @Test
    void shouldUsePermissionsFromResolver() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of()));
        RequireAccessAspect aspect = new RequireAccessAspect(
                new DefaultAuthorizationManager(List.of(new RbacAuthorizationPolicy())),
                new AccessSubjectFactory(user -> Set.of("order:update")),
                new AccessExpressionEvaluator(),
                null,
                publisher,
                null);

        assertThat(aspect.checkAccess(joinPoint(updateMethod(), new Order("o-1", "1001")))).isEqualTo("ok");
    }

    /**
     * action 为空是配置错误，应给出可操作诊断。
     */
    @Test
    void shouldRejectBlankAction() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of()));
        RequireAccessAspect aspect = aspect();
        ProceedingJoinPoint joinPoint = joinPoint(
                OrderService.class.getDeclaredMethod("blankAction"),
                new Object[0]);

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("没有配置 action");
    }

    /**
     * 类级注解应在方法没有注解时生效。
     */
    @Test
    void shouldFallBackToTypeLevelAnnotation() throws Throwable {
        authenticate(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("report:read")));
        RequireAccessAspect aspect = aspect();

        assertThat(aspect.checkAccess(joinPoint(
                ReportService.class.getDeclaredMethod("read"),
                new Object[0]))).isEqualTo("ok");
    }

    /**
     * "只能改自己的订单"用 DENY 策略表达：命中即拒绝，未命中时交回 RBAC 判定。
     */
    private AuthorizationPolicy ownerOnlyPolicy() {
        return AccessPolicyFactory.create(
                PolicyDefinition.deny(
                        "order-owner-only",
                        Set.of("order:update"),
                        List.of(ConditionDefinition.of("resource.ownerId", AttributeOperator.NOT_EQ, "subject.userId"))),
                "test");
    }

    private RequireAccessAspect aspect(AuthorizationPolicy... extraPolicies) {
        List<AuthorizationPolicy> policies = new ArrayList<>();
        policies.add(new RbacAuthorizationPolicy());
        policies.addAll(List.of(extraPolicies));
        return new RequireAccessAspect(
                new DefaultAuthorizationManager(policies),
                new AccessSubjectFactory(null),
                new AccessExpressionEvaluator(),
                null,
                publisher,
                null);
    }

    private void authenticate(LoginUser user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null, "ROLE_USER"));
    }

    private Method updateMethod() throws NoSuchMethodException {
        return OrderService.class.getDeclaredMethod("update", Order.class);
    }


    private record Order(String id, String ownerId) {
    }

    private static final class OrderService {

        @RequireAccess(
                action = "order:update",
                resourceType = "order",
                resourceId = "#order.id",
                attributes = @AccessAttribute(name = "ownerId", value = "#order.ownerId"))
        String update(Order order) {
            return "ok";
        }

        @RequireAccess(action = " ")
        String blankAction() {
            return "ok";
        }
    }

    @RequireAccess(action = "report:read")
    private static final class ReportService {

        String read() {
            return "ok";
        }
    }
}
