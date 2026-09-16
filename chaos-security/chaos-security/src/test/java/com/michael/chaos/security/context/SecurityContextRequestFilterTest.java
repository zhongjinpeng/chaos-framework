package com.michael.chaos.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文同步过滤器测试。
 */
class SecurityContextRequestFilterTest {

    @AfterEach
    void clear() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    /**
     * 请求结束后必须清理写入的用户信息，线程复用时下一个匿名请求不能读到上一个用户。
     */
    @Test
    void shouldNotLeakUserToNextRequestOnSameThread() throws Exception {
        SecurityContextRequestFilter filter = new SecurityContextRequestFilter();
        LoginUser user = new LoginUser("1001", "alice", "tenant-a", Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of()));
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                        seen[0] = RequestContext.userId();
                    }
                });

        assertThat(seen[0]).isEqualTo("1001");
        assertThat(RequestContext.userId()).isEmpty();
        assertThat(RequestContext.current()).isEmpty();
    }
}
