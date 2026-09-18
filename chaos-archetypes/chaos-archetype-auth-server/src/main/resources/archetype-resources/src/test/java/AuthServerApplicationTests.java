package ${package};

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 冒烟测试：离线即可通过，不需要 Redis。
 *
 * <p>为什么离线可以启动：Redis 连接工厂只在首次读写时建立连接；开发环境 JWK 在启动时临时生成，
 * 授权记录与客户端保存在内存中。登录（/oauth2/token）会用到 Redis 登录失败计数，因此不在这里调用，
 * 请在本地启动 Redis 后手工验证。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * JWKS 必须可公开访问，资源服务器与网关靠它验签。
     */
    @Test
    void jwkSetShouldBePublished() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    /**
     * 元数据端点要暴露 issuer，客户端据此自动发现各个端点地址。
     */
    @Test
    void authorizationServerMetadataShouldExposeIssuer() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));
    }
}
