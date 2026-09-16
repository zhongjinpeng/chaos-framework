package com.michael.chaos.security.api.datascope;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.DefaultAuthorizationManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 数据权限上下文测试。
 */
class DataScopeContextTest {

    /**
     * 清理线程上下文。
     */
    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    /**
     * 嵌套数据权限上下文退出内层后应恢复外层。
     */
    @Test
    void nestedScopeShouldRestoreOuterScope() {
        DataScopeContext.push("dept");
        DataScopeContext.push("owner");

        assertThat(DataScopeContext.current()).isEqualTo("owner");

        DataScopeContext.pop();

        assertThat(DataScopeContext.current()).isEqualTo("dept");

        DataScopeContext.pop();

        assertThat(DataScopeContext.current()).isBlank();
    }

    /**
     * set 只替换栈顶，内层 set 后外层 pop 仍能恢复更外层作用域。
     */
    @Test
    void setShouldOnlyReplaceTopOfStack() {
        DataScopeContext.push("dept");
        DataScopeContext.push("owner");

        DataScopeContext.set("region");

        assertThat(DataScopeContext.current()).isEqualTo("region");

        DataScopeContext.pop();

        assertThat(DataScopeContext.current()).isEqualTo("dept");
    }

    /**
     * 数据权限请求应可转换为通用授权请求。
     */
    @Test
    void dataScopeRequestShouldConvertToAuthorizationRequest() {
        AccessSubject subject = new AccessSubject(
                "1001",
                "alice",
                "tenant-a",
                Set.of("user"),
                Set.of("order:read"),
                Map.of("deptIds", Set.of("100", "101"))
        );
        DataScopeRequest request = new DataScopeRequest(
                "dept",
                subject,
                "OrderMapper.selectList",
                "biz_order",
                Map.of("channel", "WEB")
        );

        assertThat(request.toAuthorizationRequest("data-scope:dept").subject()).isEqualTo(subject);
        assertThat(request.toAuthorizationRequest("data-scope:dept").resource().type()).isEqualTo("data-scope");
        assertThat(request.toAuthorizationRequest("data-scope:dept").resource().id()).isEqualTo("biz_order");
        assertThat(request.toAuthorizationRequest("data-scope:dept").resource().attributes())
                .containsEntry("scope", "dept")
                .containsEntry("mappedStatementId", "OrderMapper.selectList");
    }

    /**
     * 数据权限授权服务应复用通用授权管理器。
     */
    @Test
    void dataScopeAuthorizationServiceShouldUseAuthorizationManager() {
        AuthorizationPolicy policy = new AuthorizationPolicy() {
            @Override
            public String id() {
                return "data-scope-test";
            }

            @Override
            public AuthorizationDecision decide(com.michael.chaos.security.api.access.AuthorizationRequest request) {
                if ("data-scope:dept".equals(request.action())
                        && "biz_order".equals(request.resource().id())) {
                    return AuthorizationDecision.allow(id(), "matched data scope");
                }
                return AuthorizationDecision.abstain("not matched");
            }
        };
        DataScopeAuthorizationService service = new DataScopeAuthorizationService(
                new DefaultAuthorizationManager(List.of(policy))
        );
        DataScopeRequest request = DataScopeRequest.of("dept").withSql("OrderMapper.selectList", "biz_order");

        assertThat(service.isAllowed(request)).isTrue();
    }
}
