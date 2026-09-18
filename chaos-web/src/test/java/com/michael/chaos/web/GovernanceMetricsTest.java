package com.michael.chaos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRecordStore;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRepository;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.RecordingChaosMetrics;
import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.web.advice.ResultResponseBodyAdvice;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.exception.GlobalExceptionHandler;
import com.michael.chaos.web.idempotent.DefaultIdempotentKeyGenerator;
import com.michael.chaos.web.idempotent.IdempotentInterceptor;
import com.michael.chaos.web.idempotent.IdempotentResponseReplayFilter;
import com.michael.chaos.web.ratelimit.DefaultRateLimitKeyResolver;
import com.michael.chaos.web.ratelimit.RateLimitInterceptor;
import com.michael.chaos.web.support.ClientIpResolver;
import com.michael.chaos.web.support.TestController;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

/**
 * 治理埋点测试。
 *
 * <p>限流、幂等这些"拒绝"分支此前只写日志，线上无法回答"下单成功率下跌究竟被谁挡了"。
 * 这里验证每条拒绝路径都确实上报了指标——埋点漏一个分支和没有埋点几乎一样糟。</p>
 */
class GovernanceMetricsTest {

    private final RecordingChaosMetrics metrics = new RecordingChaosMetrics();

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MockMvc mockMvc(ChaosWebProperties properties, InMemoryIdempotentRecordStore recordStore) {
        InMemoryIdempotentRepository repository = new InMemoryIdempotentRepository();
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice())
                .addInterceptors(
                        new RateLimitInterceptor(properties, new InMemoryRateLimiter(),
                                new DefaultRateLimitKeyResolver(new ClientIpResolver(List.of())), metrics),
                        new IdempotentInterceptor(repository, new DefaultIdempotentKeyGenerator(),
                                properties, recordStore, null, metrics))
                .addFilters(new IdempotentResponseReplayFilter(recordStore, properties, metrics))
                .build();
    }

    /**
     * 限流拒绝要计入指标并带 source 标签，否则线上只能看到请求变少，看不出是被谁限的。
     */
    @Test
    void shouldCountRateLimitRejections() throws Exception {
        ChaosWebProperties properties = new ChaosWebProperties();
        properties.getRateLimit().setDefaultPermitsPerSecond(1);
        MockMvc mockMvc = mockMvc(properties, new InMemoryIdempotentRecordStore());

        mockMvc.perform(get("/limited")).andExpect(status().isOk());
        mockMvc.perform(get("/limited")).andExpect(status().isTooManyRequests());

        assertThat(metrics.recorded())
                .containsExactly(ChaosMeterNames.RATE_LIMIT_REJECTED + "{" + ChaosMeterNames.TAG_SOURCE + ",web}");
    }

    /**
     * 重复请求被拒要有指标，便于区分「客户端重发」和「业务真失败」。
     */
    @Test
    void shouldCountIdempotentRejection() throws Exception {
        MockMvc mockMvc = mockMvc(new ChaosWebProperties(), new InMemoryIdempotentRecordStore());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k1")).andExpect(status().isOk());
        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k1")).andExpect(status().isConflict());

        assertThat(metrics.names()).containsExactly(ChaosMeterNames.IDEMPOTENT_REJECTED);
    }

    /**
     * 回放命中与拒绝要分开计数：两者对客户端的影响完全不同。
     */
    @Test
    void shouldCountIdempotentReplay() throws Exception {
        ChaosWebProperties properties = new ChaosWebProperties();
        properties.getIdempotent().getReplay().setEnabled(true);
        MockMvc mockMvc = mockMvc(properties, new InMemoryIdempotentRecordStore());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k2")).andExpect(status().isOk());
        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k2")).andExpect(status().isOk());

        assertThat(metrics.names()).containsExactly(ChaosMeterNames.IDEMPOTENT_REPLAYED);
    }

    /**
     * 响应体超限导致快照没保存时必须留下痕迹，否则"回放怎么不生效"只能靠翻日志排查。
     */
    @Test
    void shouldCountSkippedRecordWhenBodyTooLarge() throws Exception {
        ChaosWebProperties properties = new ChaosWebProperties();
        properties.getIdempotent().getReplay().setEnabled(true);
        properties.getIdempotent().getReplay().setMaxBodySize(DataSize.ofBytes(1));
        MockMvc mockMvc = mockMvc(properties, new InMemoryIdempotentRecordStore());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "k3")).andExpect(status().isOk());

        assertThat(metrics.recorded()).containsExactly(
                ChaosMeterNames.IDEMPOTENT_RECORD_SKIPPED + "{" + ChaosMeterNames.TAG_REASON + ",too-large}");
    }

    /**
     * 正常放行的请求不应产生任何埋点：治理指标只记录"被拦下来"的事件，
     * 否则每个请求都打点会让指标基数和成本失控。
     */
    @Test
    void shouldNotRecordAnythingForAllowedRequests() throws Exception {
        MockMvc mockMvc = mockMvc(new ChaosWebProperties(), new InMemoryIdempotentRecordStore());

        mockMvc.perform(post("/orders").header(ChaosHeaders.IDEMPOTENCY_KEY, "ok")).andExpect(status().isOk());

        assertThat(metrics.recorded()).isEmpty();
    }
}
