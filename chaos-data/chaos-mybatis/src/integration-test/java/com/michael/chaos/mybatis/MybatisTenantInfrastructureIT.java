package com.michael.chaos.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.mybatis.tenant.RequestContextTenantIdProvider;
import javax.sql.DataSource;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MyBatis Plus 多租户真实 MySQL 集成测试。
 */
@Testcontainers
class MybatisTenantInfrastructureIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("chaos_it")
            .withUsername("chaos")
            .withPassword("chaos");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withInitializer(context -> TestPropertyValues.of(
                    "spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "chaos.mybatis.tenant.enabled=true",
                    "chaos.mybatis.data-scope.enabled=false"
            ).applyTo(context));

    /**
     * 每个用例结束后清理请求上下文。
     */
    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /**
     * 租户插件应在真实 MySQL 查询中追加 tenant_id 条件。
     */
    @Test
    void shouldFilterRowsByTenantId() {
        contextRunner.run(context -> {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id BIGINT PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL,
                        order_no VARCHAR(64) NOT NULL
                    )
                    """);
            jdbcTemplate.update("DELETE FROM orders");
            jdbcTemplate.update("INSERT INTO orders(id, tenant_id, order_no) VALUES (1, 'tenant-a', 'A-1')");
            jdbcTemplate.update("INSERT INTO orders(id, tenant_id, order_no) VALUES (2, 'tenant-b', 'B-1')");

            RequestContext.set(new RequestContextSnapshot("trace-1", "span-1", "tenant-a", "user-1", "mybatis-it"));

            OrderMapper mapper = context.getBean(OrderMapper.class);

            assertThat(mapper.selectList(null))
                    .extracting(OrderEntity::getOrderNo)
                    .containsExactly("A-1");
            assertThat(mapper.countAllWithTenantPlugin()).isEqualTo(1);
        });
    }

    /**
     * 测试应用入口。
     */
    @SpringBootApplication
    @MapperScan(basePackageClasses = OrderMapper.class)
    @Import(MybatisConfiguration.class)
    static class TestApplication {

        /**
         * 避免测试应用被直接启动。
         */
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    /**
     * MyBatis Plus 测试配置。
     */
    static class MybatisConfiguration {

        /**
         * 注册 JdbcTemplate。
         */
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        /**
         * 注册租户拦截器。
         */
        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            RequestContextTenantIdProvider tenantIdProvider = new RequestContextTenantIdProvider();
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new StringValue(tenantIdProvider.currentTenantId());
                }

                @Override
                public String getTenantIdColumn() {
                    return "tenant_id";
                }
            }));
            return interceptor;
        }
    }

    /**
     * 订单测试实体。
     */
    @TableName("orders")
    public static class OrderEntity {

        @TableId
        private Long id;

        private String tenantId;

        private String orderNo;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }
    }

    /**
     * 订单测试 Mapper。
     */
    @Mapper
    public interface OrderMapper extends BaseMapper<OrderEntity> {

        /**
         * 验证注解 SQL 也会经过租户插件。
         */
        @Select("SELECT COUNT(*) FROM orders")
        long countAllWithTenantPlugin();
    }
}
