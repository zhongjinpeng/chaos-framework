package com.michael.chaos.web.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.exception.GlobalExceptionHandler;
import com.michael.chaos.web.support.ClientIpResolver;
import com.michael.chaos.web.support.TestController;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 限流拦截器测试。
 */
class RateLimitInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ChaosWebProperties(),
                new InMemoryRateLimiter(),
                new DefaultRateLimitKeyResolver(new ClientIpResolver(List.of("10.0.0.0/8"))));
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    /**
     * 超出注解许可数后返回 429。
     */
    @Test
    void shouldRejectWhenPermitsExhausted() throws Exception {
        mockMvc.perform(get("/limited")).andExpect(status().isOk());
        mockMvc.perform(get("/limited")).andExpect(status().isTooManyRequests());
    }

    /**
     * 非可信来源伪造 X-Forwarded-For 不能换取新的限流额度。
     */
    @Test
    void shouldNotBypassLimitWithForgedForwardedFor() throws Exception {
        mockMvc.perform(get("/limited").header("X-Forwarded-For", "1.1.1.1")).andExpect(status().isOk());
        mockMvc.perform(get("/limited").header("X-Forwarded-For", "2.2.2.2")).andExpect(status().isTooManyRequests());
    }

    /**
     * 来自可信代理时按真实客户端 IP 分别计数。
     */
    @Test
    void shouldLimitPerRealClientBehindTrustedProxy() throws Exception {
        mockMvc.perform(get("/limited").with(request -> {
            request.setRemoteAddr("10.0.0.2");
            return request;
        }).header("X-Forwarded-For", "198.51.100.1")).andExpect(status().isOk());
        mockMvc.perform(get("/limited").with(request -> {
            request.setRemoteAddr("10.0.0.2");
            return request;
        }).header("X-Forwarded-For", "198.51.100.2")).andExpect(status().isOk());
    }
}
