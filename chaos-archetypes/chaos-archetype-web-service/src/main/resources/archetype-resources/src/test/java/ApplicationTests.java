package ${package};

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.test.security.ChaosMockMvcSecurity;
import com.michael.chaos.test.security.TestLoginUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 冒烟测试：离线即可通过，不需要授权服务器、数据库或 Redis。
 *
 * <p>为什么离线可以启动：资源服务器的 JWT 解码器只在第一次解析 token 时才访问 jwk-set-uri；
 * 测试通过 {@link ChaosMockMvcSecurity#loginUser(LoginUser)} 直接放入已认证的登录用户，跳过 token 解析，
 * 同时仍然经过完整的 Spring Security 过滤器链、权限切面、租户上下文和统一响应包装。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

    private static final LoginUser WRITER = TestLoginUsers.user("1001")
            .tenant("tenant-a")
            .permissions("todo:read", "todo:write")
            .build();

    private static final LoginUser READER = TestLoginUsers.user("1002")
            .tenant("tenant-a")
            .permissions("todo:read")
            .build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writerShouldCreateAndListTodos() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .with(ChaosMockMvcSecurity.loginUser(WRITER))
                        .header("Idempotency-Key", "create-todo-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"写第一个用例\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.title").value("写第一个用例"));

        mockMvc.perform(get("/api/todos").with(ChaosMockMvcSecurity.loginUser(READER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("写第一个用例"));
    }

    @Test
    void readerShouldNotCreateTodos() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .with(ChaosMockMvcSecurity.loginUser(READER))
                        .header("Idempotency-Key", "create-todo-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"无权限\"}"))
                .andExpect(status().isForbidden());
    }
}
