package ${package};

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 冒烟测试：离线即可通过，不需要授权服务器、Redis 或下游服务。
 *
 * <p>为什么离线可以启动：网关的 JWT 解码器只在第一次校验 token 时访问 jwk-set-uri，路由的下游地址也只在转发时连接。
 * 未携带 token 访问受保护路由应被网关直接拒绝，请求不会到达下游。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointShouldBePublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRouteWithoutTokenShouldBeRejected() {
        webTestClient.get().uri("/api/todos")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
