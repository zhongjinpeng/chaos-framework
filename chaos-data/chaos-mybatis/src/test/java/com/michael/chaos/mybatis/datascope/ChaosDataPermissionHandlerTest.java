package com.michael.chaos.mybatis.datascope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.mybatis.tenant.ChaosMybatisProperties;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.datascope.DataScopeRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 数据权限 SQL 条件处理器测试。
 */
class ChaosDataPermissionHandlerTest {

    /**
     * 清理数据权限上下文。
     */
    @AfterEach
    void clearContext() {
        com.michael.chaos.security.api.datascope.DataScopeContext.clear();
    }

    /**
     * 默认策略下空权限条件应追加永假条件，避免误放行。
     */
    @Test
    void emptyConditionShouldDenyByDefault() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(scope -> Optional.empty(), properties);
        com.michael.chaos.security.api.datascope.DataScopeContext.set("dept");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("1 = 0");
    }

    /**
     * 数据权限值中的单引号和反斜杠必须转义，不能改写 SQL。
     */
    @Test
    void conditionValuesShouldBeEscaped() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(DataScopeCondition.in("dept_id", List.of("d1' OR '1'='1", "d2\\"))), properties);
        com.michael.chaos.security.api.datascope.DataScopeContext.set("dept");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("biz_order.dept_id IN ('d1'' OR ''1''=''1', 'd2\\\\')");
    }

    /**
     * 配置 IGNORE 后空权限条件保持原 SQL。
     */
    @Test
    void emptyConditionShouldIgnoreWhenConfigured() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        properties.getDataScope().setEmptyConditionBehavior(ChaosMybatisProperties.EmptyConditionBehavior.IGNORE);
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(scope -> Optional.empty(), properties);
        com.michael.chaos.security.api.datascope.DataScopeContext.set("dept");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression).isNull();
    }

    /**
     * 单字段 IN 条件应保持旧版本兼容。
     */
    @Test
    void shouldBuildInExpression() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(new DataScopeCondition("dept_id", List.of("100", "101"))),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("dept");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("biz_order.dept_id IN ('100', '101')");
    }

    /**
     * 新版 Provider 应收到当前主体、表名和 mappedStatementId。
     */
    @Test
    void providerShouldReceiveDataScopeRequest() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        AccessSubject subject = new AccessSubject(
                "1001",
                "alice",
                "tenant-a",
                Set.of("user"),
                Set.of(),
                Map.of()
        );
        DataScopeProvider provider = new DataScopeProvider() {
            @Override
            public Optional<DataScopeCondition> condition(String scope) {
                return Optional.empty();
            }

            @Override
            public Optional<DataScopeCondition> condition(DataScopeRequest request) {
                assertThat(request.scope()).isEqualTo("owner");
                assertThat(request.subject()).isEqualTo(subject);
                assertThat(request.tableName()).isEqualTo("biz_order");
                assertThat(request.mappedStatementId()).isEqualTo("OrderMapper.selectList");
                return Optional.of(DataScopeCondition.eq("owner_id", request.subject().userId()));
            }
        };
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(provider, properties);
        com.michael.chaos.security.api.datascope.DataScopeContext.set(DataScopeRequest.of("owner", subject));

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("biz_order.owner_id = '1001'");
    }

    /**
     * 等值条件应支持表别名。
     */
    @Test
    void shouldBuildEqExpressionWithAlias() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(new DataScopeCondition(List.of(DataScopeCondition.Rule.eq("o", "owner_id", "1001")))),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("owner");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("o.owner_id = '1001'");
    }

    /**
     * LIKE 条件应按 JSQLParser 表达式构建。
     */
    @Test
    void shouldBuildLikeExpression() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(DataScopeCondition.like("region_code", "3301%")),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("region");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("biz_order.region_code LIKE '3301%'");
    }

    /**
     * BETWEEN 条件应支持范围匹配。
     */
    @Test
    void shouldBuildBetweenExpression() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(DataScopeCondition.between("created_month", "202601", "202612")),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("month");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo("biz_order.created_month BETWEEN '202601' AND '202612'");
    }

    /**
     * 多字段规则应使用 AND 组合。
     */
    @Test
    void shouldCombineMultipleRulesWithAnd() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(new DataScopeCondition(List.of(
                        DataScopeCondition.Rule.in("dept_id", List.of("100", "101")),
                        DataScopeCondition.Rule.eq("owner_id", "1001")
                ))),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("mixed");

        Expression expression = handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList");

        assertThat(expression.toString()).isEqualTo(
                "biz_order.dept_id IN ('100', '101') AND biz_order.owner_id = '1001'"
        );
    }

    /**
     * 非法字段名应直接拒绝，避免配置型 SQL 注入。
     */
    @Test
    void shouldRejectUnsafeColumnName() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        ChaosDataPermissionHandler handler = new ChaosDataPermissionHandler(
                scope -> Optional.of(DataScopeCondition.eq("dept_id;drop table user", "100")),
                properties
        );
        com.michael.chaos.security.api.datascope.DataScopeContext.set("unsafe");

        assertThatThrownBy(() -> handler.getSqlSegment(new Table("biz_order"), null, "OrderMapper.selectList"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法");
    }
}
