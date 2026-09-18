package com.michael.chaos.security.access;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.security.annotation.AccessAttribute;
import com.michael.chaos.security.annotation.RequireAccess;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.AuthorizationResource;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.auth.SecurityUtils;
import com.michael.chaos.security.permission.PermissionDeniedException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 基于 {@link RequireAccess} 注解的统一授权切面。
 *
 * <p>把动作、资源、资源属性和环境属性组装成 {@link AuthorizationRequest}，交给 {@link AuthorizationManager}
 * 做 RBAC + ABAC 决策；拒绝时上报治理指标与审计事件，并抛出 {@link PermissionDeniedException}。</p>
 */
@Aspect
public class RequireAccessAspect {

    private final AuthorizationManager authorizationManager;

    private final AccessSubjectFactory subjectFactory;

    private final AccessExpressionEvaluator expressionEvaluator;

    private final AuthorizationContextContributor contextContributor;

    private final AuditEventPublisher auditEventPublisher;

    private final ChaosMetrics metrics;

    /**
     * 创建统一授权切面。
     *
     * @param authorizationManager 授权决策服务
     * @param subjectFactory 访问主体工厂
     * @param expressionEvaluator 表达式求值器
     * @param contextContributor 环境属性贡献者（多个时用 CompositeAuthorizationContextContributor 合并），可为 null
     * @param auditEventPublisher 审计事件发布器，可为 null
     * @param metrics 治理指标，可为 null
     */
    public RequireAccessAspect(
            AuthorizationManager authorizationManager,
            AccessSubjectFactory subjectFactory,
            AccessExpressionEvaluator expressionEvaluator,
            AuthorizationContextContributor contextContributor,
            AuditEventPublisher auditEventPublisher,
            ChaosMetrics metrics) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager must not be null");
        this.subjectFactory = subjectFactory == null ? new AccessSubjectFactory(null) : subjectFactory;
        this.expressionEvaluator = expressionEvaluator == null ? new AccessExpressionEvaluator() : expressionEvaluator;
        this.contextContributor = contextContributor;
        this.auditEventPublisher = auditEventPublisher;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 执行业务方法前做统一授权决策。
     */
    @Around("@within(com.michael.chaos.security.annotation.RequireAccess)"
            + " || @annotation(com.michael.chaos.security.annotation.RequireAccess)")
    public Object checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireAccess requireAccess = resolveAnnotation(method, signature.getDeclaringType());
        if (requireAccess == null) {
            return joinPoint.proceed();
        }
        String action = requireAccess.action().trim();
        if (action.isBlank()) {
            throw missingAction(method);
        }
        Optional<LoginUser> currentUser = SecurityUtils.currentUser();
        AuthorizationRequest request = AuthorizationRequest
                .builder(currentUser.map(subjectFactory).orElse(AccessSubject.ANONYMOUS), action)
                .resource(resource(requireAccess, method, joinPoint, currentUser.orElse(null)))
                .contribute(contextContributor)
                .build();

        AuthorizationDecision decision = authorizationManager.decide(request);
        if (decision.allowed()) {
            return joinPoint.proceed();
        }
        denied(action, request, decision);
        throw new PermissionDeniedException();
    }

    private AuthorizationResource resource(
            RequireAccess requireAccess,
            Method method,
            ProceedingJoinPoint joinPoint,
            LoginUser user) {
        AuthorizationResource.Builder builder = AuthorizationResource.builder(requireAccess.resourceType().trim())
                .id(expressionEvaluator.evaluateAsString(
                        requireAccess.resourceId(),
                        method,
                        joinPoint.getArgs(),
                        joinPoint.getTarget(),
                        user));
        for (AccessAttribute attribute : requireAccess.attributes()) {
            String name = attribute.name() == null ? "" : attribute.name().trim();
            if (name.isBlank()) {
                throw missingAttributeName(method);
            }
            builder.attribute(name, expressionEvaluator.evaluate(
                    attribute.value(),
                    method,
                    joinPoint.getArgs(),
                    joinPoint.getTarget(),
                    user));
        }
        return builder.build();
    }

    private void denied(String action, AuthorizationRequest request, AuthorizationDecision decision) {
        metrics.increment(ChaosMeterNames.SECURITY_ACCESS_DENIED, ChaosMeterNames.TAG_SOURCE, "require-access");
        if (auditEventPublisher == null) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("action", action);
        attributes.put("policy", decision.policyId());
        if (!request.resource().type().isBlank()) {
            attributes.put("resourceType", request.resource().type());
        }
        if (!request.resource().id().isBlank()) {
            attributes.put("resourceId", request.resource().id());
        }
        auditEventPublisher.publish(AuditSupport.event(AuditAction.SECURITY_PERMISSION_DENIED, AuditOutcome.DENIED)
                .principalId(request.subject().userId())
                .tenantId(request.subject().tenantId())
                .reason(decision.reason().isBlank() ? "access denied" : decision.reason())
                .attributes(Map.copyOf(attributes))
                .build());
    }

    private RequireAccess resolveAnnotation(Method method, Class<?> declaringType) {
        RequireAccess methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, RequireAccess.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(declaringType, RequireAccess.class);
    }

    private ChaosDiagnosticException missingAction(Method method) {
        return new ChaosDiagnosticException(new ChaosDiagnostic(
                location(method) + " 的 @RequireAccess 没有配置 action",
                List.of("action 同时用作 RBAC 权限编码和 ABAC 策略的动作匹配，为空时无法做出任何决策"),
                List.of("补充 action，例如 @RequireAccess(action = \"order:update\")")));
    }

    private ChaosDiagnosticException missingAttributeName(Method method) {
        return new ChaosDiagnosticException(new ChaosDiagnostic(
                location(method) + " 的 @AccessAttribute 没有配置 name",
                List.of("属性名为空时，ABAC 条件无法通过 resource.<name> 引用该属性"),
                List.of("补充属性名，例如 @AccessAttribute(name = \"ownerId\", value = \"#order.ownerId\")")));
    }

    private String location(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
