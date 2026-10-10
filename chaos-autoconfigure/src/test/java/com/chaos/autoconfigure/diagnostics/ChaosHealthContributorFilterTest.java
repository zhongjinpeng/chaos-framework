package com.chaos.autoconfigure.diagnostics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.mock.env.MockEnvironment;

/**
 * 低价值健康指标过滤测试。
 */
class ChaosHealthContributorFilterTest {

    /**
     * Servlet 与 WebFlux 注册表都移除默认关闭项，业务显式开启的指标保持注册。
     */
    @Test
    void shouldFilterDisabledContributorsAndKeepExplicitlyEnabledOnes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.health.ping.enabled", "true");
        ChaosHealthContributorFilter filter = new ChaosHealthContributorFilter(environment);
        HealthContributorRegistry blocking = mock(HealthContributorRegistry.class);
        ReactiveHealthContributorRegistry reactive = mock(ReactiveHealthContributorRegistry.class);

        filter.postProcessAfterInitialization(blocking, "healthContributorRegistry");
        filter.postProcessAfterInitialization(reactive, "reactiveHealthContributorRegistry");

        verify(blocking, never()).unregisterContributor("ping");
        verify(reactive, never()).unregisterContributor("ping");
        for (String contributor : new String[] {
            "discoveryComposite", "reactiveDiscoveryClients", "refreshScope", "ssl"
        }) {
            verify(blocking).unregisterContributor(contributor);
            verify(reactive).unregisterContributor(contributor);
        }
    }
}
