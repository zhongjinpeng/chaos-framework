package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动入口。
 *
 * <p>路由写在 {@code application.yml} 的 {@code spring.cloud.gateway.server.webflux.routes} 中；
 * 鉴权、租户、限流、黑名单、身份头剥离等治理能力由 chaos-gateway-starter 自动装配，无需编写代码。
 * 需要自定义过滤器时，声明 {@code GlobalFilter} Bean 并注意与 chaos 过滤器的顺序（见 chaos-gateway 文档）。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
