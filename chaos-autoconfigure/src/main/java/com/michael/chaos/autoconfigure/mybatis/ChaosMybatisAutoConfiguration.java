package com.michael.chaos.autoconfigure.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.IdentifierGeneratorAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.michael.chaos.autoconfigure.support.ProductionSafety;
import com.michael.chaos.mybatis.audit.AuditorProvider;
import com.michael.chaos.mybatis.audit.ChaosMetaObjectHandler;
import com.michael.chaos.mybatis.audit.LoginUserAuditorProvider;
import com.michael.chaos.mybatis.datascope.ChaosDataPermissionHandler;
import com.michael.chaos.mybatis.datascope.DataScopeProvider;
import com.michael.chaos.mybatis.datascope.NoopDataScopeProvider;
import com.michael.chaos.mybatis.observability.RequestTimingMybatisInterceptor;
import com.michael.chaos.mybatis.tenant.ChaosMybatisProperties;
import com.michael.chaos.mybatis.tenant.ChaosTenantLineHandler;
import com.michael.chaos.mybatis.tenant.RequestContextTenantIdProvider;
import com.michael.chaos.mybatis.tenant.TenantIdProvider;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import java.net.InetAddress;
import java.util.Map;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * MyBatis Plus starter 自动装配。
 */
@AutoConfiguration(before = {IdentifierGeneratorAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
@ConditionalOnClass(value = MybatisPlusInterceptor.class, name = "com.michael.chaos.mybatis.tenant.ChaosMybatisProperties")
@EnableConfigurationProperties(ChaosMybatisProperties.class)

public class ChaosMybatisAutoConfiguration {


    /**
     * 注册 MyBatis 请求阶段耗时聚合插件。
     */
    @Bean
    @ConditionalOnMissingBean(name = "requestTimingMybatisInterceptor")
    public Interceptor requestTimingMybatisInterceptor(ChaosMybatisProperties properties) {
        return new RequestTimingMybatisInterceptor(properties.getDbType().name());
    }

    /**
     * 注册默认租户 ID 提供者。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantIdProvider tenantIdProvider() {
        return new RequestContextTenantIdProvider();
    }

    /**
     * 注册默认操作人提供者：读取 RequestContext。
     *
     * <p>存在 chaos-security-api 时由 {@link LoginUserAuditorConfiguration} 优先注册基于登录用户的实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditorProvider auditorProvider() {
        return ChaosMetaObjectHandler.defaultAuditorProvider();
    }

    /**
     * 基于登录用户的操作人装配。
     *
     * <p>放在嵌套配置里并以 {@code @ConditionalOnClass} 保护：chaos-security-api 是可选依赖，
     * 顶层方法签名引用 {@link LoginUserProvider} 会让未引入安全模块的应用在解析配置类时失败。
     * 嵌套配置先于外层 {@code @Bean} 方法处理，因此外层默认实现的 {@code @ConditionalOnMissingBean} 能看到它。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.michael.chaos.security.api.auth.LoginUserProvider")
    static class LoginUserAuditorConfiguration {

        /**
         * 存在 {@link LoginUserProvider} Bean 时优先使用登录用户作为操作人，未登录时回退到 RequestContext。
         *
         * <p>使用 {@link ObjectProvider} 延迟获取而不是 {@code @ConditionalOnBean}：安全自动装配与本配置之间没有顺序约束，
         * 条件注解在 Bean 定义阶段求值可能看不到尚未注册的 {@link LoginUserProvider}。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        public AuditorProvider auditorProvider(ObjectProvider<LoginUserProvider> loginUserProvider) {
            AuditorProvider fallback = ChaosMetaObjectHandler.defaultAuditorProvider();
            LoginUserProvider provider = loginUserProvider.getIfAvailable();
            return provider == null ? fallback : new LoginUserAuditorProvider(provider, fallback);
        }
    }

    /**
     * 注册审计字段自动填充处理器。
     *
     * <p>不再要求 classpath 存在 chaos-security：时间字段和逻辑删除标记必须始终填充。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler(ObjectProvider<AuditorProvider> auditorProvider) {
        return new ChaosMetaObjectHandler(auditorProvider.getIfAvailable(ChaosMetaObjectHandler::defaultAuditorProvider));
    }

    /**
     * 注册默认空数据权限提供者。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataScopeProvider dataScopeProvider() {
        return new NoopDataScopeProvider();
    }

    /**
     * 注册默认雪花 ID 生成器。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdentifierGenerator identifierGenerator(ChaosMybatisProperties properties) {
        ChaosMybatisProperties.IdGenerator idGenerator = properties.getIdGenerator();
        Long workerId = idGenerator.getWorkerId();
        Long datacenterId = idGenerator.getDatacenterId();
        if (workerId == null && datacenterId == null) {
            return new DefaultIdentifierGenerator((InetAddress) null);
        }
        if (workerId == null || datacenterId == null) {
            throw new IllegalStateException(
                    "chaos.mybatis.id-generator.worker-id and datacenter-id must be configured together");
        }
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

    /**
     * 默认使用 MyBatis Plus ASSIGN_ID，让未显式指定主键策略的实体走雪花 ID。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosMybatisIdTypeCustomizer")
    public MybatisPlusPropertiesCustomizer chaosMybatisIdTypeCustomizer() {
        return properties -> {
            GlobalConfig globalConfig = properties.getGlobalConfig();
            if (globalConfig == null) {
                globalConfig = new GlobalConfig();
                properties.setGlobalConfig(globalConfig);
            }
            GlobalConfig.DbConfig dbConfig = globalConfig.getDbConfig();
            if (dbConfig == null) {
                dbConfig = new GlobalConfig.DbConfig();
                globalConfig.setDbConfig(dbConfig);
            }
            if (dbConfig.getIdType() == null) {
                dbConfig.setIdType(IdType.ASSIGN_ID);
            }
        };
    }

    /**
     * 生产环境禁止使用空数据权限提供者。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosMybatisProductionSafetyChecker")
    public SmartInitializingSingleton chaosMybatisProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            ChaosMybatisProperties properties) {
        return () -> {
            if (!properties.getDataScope().isEnabled()) {
                return;
            }
            ProductionSafety.warnUnsafeDefaultBeans(
                    environment,
                    beanFactory,
                    Map.of(
                            NoopDataScopeProvider.class.getName(),
                            "chaos-mybatis: 生产环境不能使用 NoopDataScopeProvider，请接入真实数据权限 Provider 或关闭数据权限"
                    )
            );
        };
    }

    /**
     * 注册 MyBatis Plus 内置拦截器。
     *
     * <p>顺序遵循 MyBatis Plus 推荐：多租户 → 数据权限 → 分页 → 乐观锁。分页设置 {@code maxLimit}，
     * 防止客户端传入超大 size 触发全表查询。数据权限上下文来自 chaos-security-api（chaos-mybatis 的必需依赖）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            TenantIdProvider tenantIdProvider,
            DataScopeProvider dataScopeProvider,
            ChaosMybatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (properties.getTenant().isEnabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new ChaosTenantLineHandler(tenantIdProvider, properties)));
        }
        if (properties.getDataScope().isEnabled()) {
            interceptor.addInnerInterceptor(new DataPermissionInterceptor(new ChaosDataPermissionHandler(dataScopeProvider, properties)));
        }
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
        long maxLimit = properties.getPagination().getMaxLimit();
        if (maxLimit > 0) {
            pagination.setMaxLimit(maxLimit);
        }
        pagination.setOverflow(properties.getPagination().isOverflow());
        interceptor.addInnerInterceptor(pagination);
        if (properties.getOptimisticLock().isEnabled()) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        return interceptor;
    }
}
