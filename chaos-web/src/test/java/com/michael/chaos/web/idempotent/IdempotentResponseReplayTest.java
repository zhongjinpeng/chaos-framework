package com.michael.chaos.web.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRecordStore;
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
import org.springframework.util.unit.DataSize;

/**
 * 幂等响应回放测试。
 *
 * <p>必须走 MockMvc 完整链路：响应体由消息转换器写入，只有真正经过
 * {@link IdempotentResponseReplayFilter} 的 {@code ContentCachingResponseWrapper} 才能被采集到。</p>
 */
class IdempotentResponseReplayTest {

    private MockMvc mockMvc;

    private TestController controller;

    private InMemoryIdempotentRepository repository;

    private InMemoryIdempotentRecordStore recordStore;

    private ChaosWebProperties properties;

    @BeforeEach
    void setUp() {
        controller = new TestController();
        repository = new InMemoryIdempotentRepository();
        recordStore = new InMemoryIdempotentRecordStore();
        properties = new ChaosWebProperties();
        properties.getIdempotent().getReplay().setEnabled(true);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice())
                .addInterceptors(new IdempotentInterceptor(
                        repository, new DefaultIdempotentKeyGenerator(), properties, recordStore, null))
                .addFilters(new IdempotentResponseReplayFilter(recordStore, properties))
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /**
     * 重复请求返回与首次完全一致的响应体，且业务只执行一次。
     *
     * <p>这是本次改造的核心：改造前重复请求拿到 409，客户端无法据此判断第一次是否成功。</p>
     */
    @Test
    void shouldReplayFirstResponseOnDuplicate() throws Exception {
        String first = mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ChaosHeaders.IDEMPOTENCY_REPLAYED))
                .andReturn().getResponse().getContentAsString();

        String replayed = mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k1"))
                .andExpect(status().isOk())
                .andExpect(header().string(ChaosHeaders.IDEMPOTENCY_REPLAYED, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replayed).isEqualTo(first);
        assertThat(controller.created).hasValue(1);
    }

    /**
     * 创建类接口的状态码和 Location 也要一并回放，否则回放响应与首次不等价。
     */
    @Test
    void shouldReplayStatusAndLocationHeader() throws Exception {
        mockMvc.perform(post("/orders/created").header(ChaosHeaders.IDEMPOTENCY_KEY, "k2"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/1"));

        mockMvc.perform(post("/orders/created").header(ChaosHeaders.IDEMPOTENCY_KEY, "k2"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/1"))
                .andExpect(header().string(ChaosHeaders.IDEMPOTENCY_REPLAYED, "true"))
                .andExpect(jsonPath("$.data.id").value(1));

        assertThat(controller.created).hasValue(1);
    }

    /**
     * 首次请求失败不留快照，客户端用同一个 key 重试应真正重新执行。
     */
    @Test
    void shouldNotReplayFailedResponse() throws Exception {
        mockMvc.perform(post("/orders").param("fail", "true").header(ChaosHeaders.IDEMPOTENCY_KEY, "k3"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k3"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ChaosHeaders.IDEMPOTENCY_REPLAYED));

        assertThat(controller.created).hasValue(1);
    }

    /**
     * 首次请求还在执行中（占位已被抢占但还没有快照）时返回 409，而不是回放一个不存在的响应。
     */
    @Test
    void shouldRejectWhileFirstRequestIsStillInFlight() throws Exception {
        String inFlightKey = new DefaultIdempotentKeyGenerator().generate(
                new IdempotentKeyContext("", "k4", "POST", "/orders", "", "", ""));
        repository.saveIfAbsent(inFlightKey, properties.getIdempotent().getTtl());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k4"))
                .andExpect(status().isConflict());

        assertThat(controller.created).hasValue(0);
    }

    /**
     * {@code @Idempotent(replay = false)} 的接口保持原有的 409 行为。
     */
    @Test
    void shouldKeepRejectingWhenAnnotationDisablesReplay() throws Exception {
        mockMvc.perform(post("/orders/no-replay").header(ChaosHeaders.IDEMPOTENCY_KEY, "k5"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/orders/no-replay").header(ChaosHeaders.IDEMPOTENCY_KEY, "k5"))
                .andExpect(status().isConflict());

        assertThat(recordStore.size()).isZero();
    }

    /**
     * 响应体超过上限时不保存快照，重复请求退回 409，避免大响应撑爆存储。
     */
    @Test
    void shouldSkipRecordWhenBodyExceedsLimit() throws Exception {
        properties.getIdempotent().getReplay().setMaxBodySize(DataSize.ofBytes(1));

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k6"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k6"))
                .andExpect(status().isConflict());

        assertThat(recordStore.size()).isZero();
    }

    /**
     * 不同用户使用相同 Idempotency-Key 不应互相回放对方的响应。
     */
    @Test
    void shouldIsolateReplayByUser() throws Exception {
        RequestContext.setUserId("user-a");
        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "same")).andExpect(status().isOk());
        RequestContext.setUserId("user-b");
        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "same"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ChaosHeaders.IDEMPOTENCY_REPLAYED));

        assertThat(controller.created).hasValue(2);
    }
}
