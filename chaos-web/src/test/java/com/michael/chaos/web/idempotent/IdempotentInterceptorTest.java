package com.michael.chaos.web.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRepository;
import com.michael.chaos.web.advice.ResultResponseBodyAdvice;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.exception.GlobalExceptionHandler;
import com.michael.chaos.web.support.TestController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 幂等拦截器测试。
 *
 * <p>必须经过 MockMvc 完整链路：业务异常被 GlobalExceptionHandler 处理后 afterCompletion 的 ex 为 null，
 * 只有这样才能复现"失败后 key 不释放"的问题。</p>
 */
class IdempotentInterceptorTest {

    private MockMvc mockMvc;

    private TestController controller;

    private InMemoryIdempotentRepository repository;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        repository = new InMemoryIdempotentRepository();
        IdempotentInterceptor interceptor = new IdempotentInterceptor(
                repository, new DefaultIdempotentKeyGenerator(), new ChaosWebProperties());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice())
                .addInterceptors(interceptor)
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /**
     * 成功请求保留占位，重复提交返回 409。
     */
    @Test
    void shouldRejectDuplicateAfterSuccess() throws Exception {
        mockMvc.perform(post("/orders").header("Idempotency-Key", "k1")).andExpect(status().isOk());
        mockMvc.perform(post("/orders").header("Idempotency-Key", "k1")).andExpect(status().isConflict());

        assertThat(controller.created).hasValue(1);
    }

    /**
     * 业务失败（已被全局异常处理器转换为 400）后必须释放占位，允许客户端重试。
     */
    @Test
    void shouldReleaseKeyWhenHandledExceptionProducesErrorResponse() throws Exception {
        mockMvc.perform(post("/orders").param("fail", "true").header("Idempotency-Key", "k2"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/orders").header("Idempotency-Key", "k2")).andExpect(status().isOk());
        assertThat(repository.size()).isEqualTo(1);
    }

    /**
     * 缺少幂等 key 时默认拒绝，而不是用 traceId 兜底假装受保护。
     */
    @Test
    void shouldRejectMissingKeyByDefault() throws Exception {
        mockMvc.perform(post("/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
        assertThat(controller.created).hasValue(0);
    }

    /**
     * requireKey=false 时缺少 key 直接放行。
     */
    @Test
    void shouldSkipWhenKeyOptional() throws Exception {
        mockMvc.perform(post("/orders/optional-key")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/optional-key")).andExpect(status().isOk());
        assertThat(controller.created).hasValue(2);
    }

    /**
     * 不同用户使用相同 Idempotency-Key 不应互相阻塞。
     */
    @Test
    void shouldIsolateKeysByUser() throws Exception {
        RequestContext.setUserId("user-a");
        mockMvc.perform(post("/orders").header("Idempotency-Key", "same")).andExpect(status().isOk());
        RequestContext.setUserId("user-b");
        mockMvc.perform(post("/orders").header("Idempotency-Key", "same")).andExpect(status().isOk());

        assertThat(controller.created).hasValue(2);
    }
}
