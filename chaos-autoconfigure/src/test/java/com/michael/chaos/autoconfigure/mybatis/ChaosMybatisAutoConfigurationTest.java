package com.michael.chaos.autoconfigure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.michael.chaos.autoconfigure.support.ProductionSafety;
import com.michael.chaos.mybatis.audit.AuditorProvider;
import com.michael.chaos.mybatis.audit.LoginUserAuditorProvider;
import com.michael.chaos.mybatis.audit.RequestContextAuditorProvider;
import com.michael.chaos.mybatis.tenant.ChaosMybatisProperties;
import com.michael.chaos.mybatis.tenant.TenantIdProvider;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import java.util.Optional;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 自动装配测试。
 */
class ChaosMybatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosMybatisAutoConfiguration.class));

    /**
     * 存在登录用户 SPI 时操作人取自登录用户；否则回退到 RequestContext。持久层不直接依赖 chaos-security。
     */
    @Test
    void auditorShouldComeFromLoginUserProviderWhenPresent() {
        contextRunner.run(context -> assertThat(context.getBean(AuditorProvider.class))
                .isInstanceOf(RequestContextAuditorProvider.class));
        contextRunner.withBean(LoginUserProvider.class, () -> Optional::empty)
                .run(context -> assertThat(context.getBean(AuditorProvider.class))
                        .isInstanceOf(LoginUserAuditorProvider.class));
    }

    /**
     * 默认缺失租户策略应拒绝，避免直连后端时误放行。
     */
    @Test
    void missingTenantShouldDenyByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosMybatisProperties.class);
            assertThat(context.getBean(ChaosMybatisProperties.class)
                    .getTenant()
                    .getMissingTenantBehavior())
                    .isEqualTo(ChaosMybatisProperties.MissingTenantBehavior.DENY);
            assertThat(context.getBean(ChaosMybatisProperties.class).getDbType()).isEqualTo(DbType.MYSQL);
            assertThat(context.getBean(ChaosMybatisProperties.class)
                    .getDataScope()
                    .getEmptyConditionBehavior())
                    .isEqualTo(ChaosMybatisProperties.EmptyConditionBehavior.DENY);
            assertThat(context).hasSingleBean(IdentifierGenerator.class);
            assertThat(context.getBean(IdentifierGenerator.class)).isInstanceOf(DefaultIdentifierGenerator.class);
            TenantLineInnerInterceptor tenantInterceptor = tenantInterceptor(context.getBean(MybatisPlusInterceptor.class));
            assertThatThrownBy(() -> tenantInterceptor.getTenantLineHandler().getTenantId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing tenant id");
        });
    }

    /**
     * 数据库类型和缺失租户策略应支持配置覆盖。
     */
    @Test
    void shouldBindDbTypeAndMissingTenantBehavior() {
        contextRunner.withPropertyValues(
                        "chaos.mybatis.db-type=postgresql",
                        "chaos.mybatis.tenant.missing-tenant-behavior=ignore"
                )
                .run(context -> {
                    ChaosMybatisProperties properties = context.getBean(ChaosMybatisProperties.class);

                    assertThat(properties.getDbType()).isEqualTo(DbType.POSTGRE_SQL);
                    assertThat(properties.getTenant().getMissingTenantBehavior())
                            .isEqualTo(ChaosMybatisProperties.MissingTenantBehavior.IGNORE);
                    TenantLineInnerInterceptor tenantInterceptor =
                            tenantInterceptor(context.getBean(MybatisPlusInterceptor.class));
                    assertThat(tenantInterceptor.getTenantLineHandler().ignoreTable("orders")).isTrue();
                    assertThat(tenantInterceptor.getTenantLineHandler().getTenantId())
                            .isInstanceOfSatisfying(StringValue.class,
                                    expression -> assertThat(expression.getValue()).isEmpty());
                });
    }

    /**
     * 嵌套配置和关键枚举被设置为 null 时应恢复安全默认值。
     */
    @Test
    void shouldRestoreDefaultPropertiesWhenSetToNull() {
        ChaosMybatisProperties properties = new ChaosMybatisProperties();

        properties.setDbType(null);
        properties.setTenant(null);
        properties.setDataScope(null);
        properties.setIdGenerator(null);
        properties.getTenant().setMissingTenantBehavior(null);
        properties.getDataScope().setEmptyConditionBehavior(null);

        assertThat(properties.getDbType()).isEqualTo(DbType.MYSQL);
        assertThat(properties.getTenant()).isNotNull();
        assertThat(properties.getTenant().getMissingTenantBehavior())
                .isEqualTo(ChaosMybatisProperties.MissingTenantBehavior.DENY);
        assertThat(properties.getDataScope()).isNotNull();
        assertThat(properties.getDataScope().getEmptyConditionBehavior())
                .isEqualTo(ChaosMybatisProperties.EmptyConditionBehavior.DENY);
        assertThat(properties.getIdGenerator()).isNotNull();
    }

    /**
     * 手工指定雪花 worker 和 datacenter 时必须成对配置。
     */
    @Test
    void shouldRejectPartialIdGeneratorConfig() {
        contextRunner.withPropertyValues("chaos.mybatis.id-generator.worker-id=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("worker-id and datacenter-id must be configured together");
                });
    }

    /**
     * 业务自定义 IdentifierGenerator 时应优先使用业务 Bean。
     */
    @Test
    void shouldBackOffWhenIdentifierGeneratorExists() {
        contextRunner.withUserConfiguration(CustomIdentifierGeneratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentifierGenerator.class);
                    assertThat(context.getBean(IdentifierGenerator.class).nextId(new Object())).isEqualTo(42L);
                });
    }

    /**
     * 未显式指定全局主键策略时默认使用 ASSIGN_ID。
     */
    @Test
    void shouldCustomizeDefaultIdType() {
        contextRunner.run(context -> {
            MybatisPlusPropertiesCustomizer customizer = context.getBean(MybatisPlusPropertiesCustomizer.class);

            MybatisPlusProperties emptyProperties = new MybatisPlusProperties();
            emptyProperties.setGlobalConfig(null);
            customizer.customize(emptyProperties);
            assertThat(emptyProperties.getGlobalConfig().getDbConfig().getIdType()).isEqualTo(IdType.ASSIGN_ID);

            MybatisPlusProperties explicitProperties = new MybatisPlusProperties();
            explicitProperties.setGlobalConfig(new GlobalConfig()
                    .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO)));
            customizer.customize(explicitProperties);
            assertThat(explicitProperties.getGlobalConfig().getDbConfig().getIdType()).isEqualTo(IdType.AUTO);
        });
    }

    /**
     * 分页插件默认限制单页最大条数，并注册乐观锁插件和审计填充。
     */
    @Test
    void shouldLimitPageSizeAndRegisterOptimisticLock() {
        contextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors())
                    .filteredOn(PaginationInnerInterceptor.class::isInstance)
                    .singleElement()
                    .extracting(inner -> ((PaginationInnerInterceptor) inner).getMaxLimit())
                    .isEqualTo(500L);
            assertThat(interceptor.getInterceptors()).hasAtLeastOneElementOfType(OptimisticLockerInnerInterceptor.class);
            assertThat(context).hasSingleBean(MetaObjectHandler.class);
        });
    }

    /**
     * 分页上限和乐观锁可以通过配置调整。
     */
    @Test
    void shouldBindPaginationAndOptimisticLockProperties() {
        contextRunner.withPropertyValues(
                        "chaos.mybatis.pagination.max-limit=100",
                        "chaos.mybatis.optimistic-lock.enabled=false")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
                    assertThat(interceptor.getInterceptors())
                            .filteredOn(PaginationInnerInterceptor.class::isInstance)
                            .singleElement()
                            .extracting(inner -> ((PaginationInnerInterceptor) inner).getMaxLimit())
                            .isEqualTo(100L);
                    assertThat(interceptor.getInterceptors())
                            .noneMatch(OptimisticLockerInnerInterceptor.class::isInstance);
                });
    }

    /**
     * 默认租户 ID 提供者应注册。
     */
    @Test
    void shouldRegisterTenantIdProvider() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TenantIdProvider.class));
    }

    /**
     * 生产模式发现空数据权限提供者时默认阻断启动（与 ProductionSafety fail-fast 保持一致）。
     */
    @Test
    void shouldRejectNoopDataScopeProviderInProductionMode() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("NoopDataScopeProvider");
                });
    }

    /**
     * 关闭 fail-fast 后只告警，不阻断启动。
     */
    @Test
    void shouldOnlyWarnAboutNoopDataScopeProviderWhenFailFastDisabled() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.fail-fast=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 生产模式关闭数据权限时允许保留默认空提供者。
     */
    @Test
    void shouldAllowNoopDataScopeProviderWhenDataScopeIsDisabledInProductionMode() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.mybatis.data-scope.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChaosMybatisProperties.class);
                    assertThat(context.getBean(ChaosMybatisProperties.class)
                            .getDataScope()
                            .isEnabled())
                            .isFalse();
                });
    }

    private TenantLineInnerInterceptor tenantInterceptor(MybatisPlusInterceptor interceptor) {
        return interceptor.getInterceptors().stream()
                .filter(TenantLineInnerInterceptor.class::isInstance)
                .map(TenantLineInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomIdentifierGeneratorConfiguration {

        @Bean
        IdentifierGenerator customIdentifierGenerator() {
            return entity -> 42L;
        }
    }
}
