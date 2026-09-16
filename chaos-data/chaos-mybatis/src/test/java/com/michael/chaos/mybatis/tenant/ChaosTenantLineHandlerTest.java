package com.michael.chaos.mybatis.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;

/**
 * 多租户 SQL 改写处理器测试。
 */
class ChaosTenantLineHandlerTest {

    /**
     * 合法租户 ID 应生成租户条件。
     */
    @Test
    void shouldAppendTenantCondition() throws Exception {
        ChaosTenantLineHandler handler = new ChaosTenantLineHandler(() -> "tenant-a", new ChaosMybatisProperties());

        assertThat(rewrite(handler, "select * from biz_order")).isEqualTo("SELECT * FROM biz_order WHERE tenant_id = 'tenant-a'");
    }

    /**
     * 带单引号的租户 ID 不能改写 SQL，应被白名单直接拒绝。
     */
    @Test
    void shouldRejectInjectedTenantId() {
        ChaosTenantLineHandler handler = new ChaosTenantLineHandler(() -> "x' OR '1'='1", new ChaosMybatisProperties());

        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal tenant id");
    }

    /**
     * 非法租户的错误信息给出修复方式，但不回显租户值本身（可能是注入载荷）。
     */
    @Test
    void illegalTenantMessageShouldBeActionableWithoutEchoingValue() {
        ChaosTenantLineHandler handler = new ChaosTenantLineHandler(() -> "x' OR '1'='1", new ChaosMybatisProperties());

        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("怎么修：")
                .hasMessageContaining("chaos.mybatis.tenant.id-pattern")
                .hasMessageNotContaining("OR '1'='1");
    }

    /**
     * 放宽白名单后仍必须转义，不能产生注入。
     */
    @Test
    void shouldEscapeWhenPatternIsRelaxed() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();
        properties.getTenant().setIdPattern(".*");
        ChaosTenantLineHandler handler = new ChaosTenantLineHandler(() -> "x' OR '1'='1", properties);

        assertThat(handler.getTenantId().toString()).isEqualTo("'x'' OR ''1''=''1'");
    }

    /**
     * 提供者返回 null 时按缺失租户处理，默认拒绝。
     */
    @Test
    void shouldDenyWhenTenantIdIsNull() {
        ChaosTenantLineHandler handler = new ChaosTenantLineHandler(() -> null, new ChaosMybatisProperties());

        assertThatThrownBy(handler::getTenantId).hasMessageContaining("Missing tenant id");
    }

    private static String rewrite(ChaosTenantLineHandler handler, String sql) throws Exception {
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(handler);
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        return interceptor.parserSingle(select.toString(), null);
    }
}
