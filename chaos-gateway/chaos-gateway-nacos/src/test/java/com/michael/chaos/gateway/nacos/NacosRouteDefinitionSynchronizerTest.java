package com.michael.chaos.gateway.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.Set;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.test.StepVerifier;

/**
 * Nacos 路由同步器测试。
 */
class NacosRouteDefinitionSynchronizerTest {

    private static final String VALID_CONFIG = """
            routes:
              - id: order-service
                uri: lb://order-service
                predicates:
                  - name: Path
                    args:
                      _genkey_0: /order/**
            """;

    /**
     * 启动时应拉取配置、注册监听器并发布刷新事件。
     */
    @Test
    void shouldLoadInitialRoutesAndRegisterListener() {
        CapturingConfigService configService = new CapturingConfigService(VALID_CONFIG);
        NacosRouteDefinitionRepository repository = new NacosRouteDefinitionRepository();
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        NacosRouteDefinitionSynchronizer synchronizer = synchronizer(configService, repository, eventPublisher, false);

        synchronizer.start();

        assertThat(synchronizer.isRunning()).isTrue();
        assertThat(configService.listener).isNotNull();
        assertThat(eventPublisher.refreshEvents).isEqualTo(1);
        StepVerifier.create(repository.getRouteDefinitions())
                .assertNext(route -> assertThat(route.getId()).isEqualTo("order-service"))
                .verifyComplete();
    }

    /**
     * 监听到非法配置时应保留上一次有效路由。
     */
    @Test
    void shouldKeepLastValidRoutesWhenRefreshFails() {
        CapturingConfigService configService = new CapturingConfigService(VALID_CONFIG);
        NacosRouteDefinitionRepository repository = new NacosRouteDefinitionRepository();
        NacosRouteDefinitionSynchronizer synchronizer = synchronizer(
                configService,
                repository,
                new CapturingEventPublisher(),
                false
        );
        synchronizer.start();

        configService.listener.receiveConfigInfo("""
                routes:
                  - id: invalid
                    uri: lb://invalid
                """);

        StepVerifier.create(repository.getRouteDefinitions())
                .assertNext(route -> assertThat(route.getId()).isEqualTo("order-service"))
                .verifyComplete();
    }

    /**
     * fail-fast 开启后初始空配置应阻止启动。
     */
    @Test
    void shouldFailFastWhenInitialConfigIsEmpty() {
        CapturingConfigService configService = new CapturingConfigService("");
        NacosRouteDefinitionRepository repository = new NacosRouteDefinitionRepository();
        NacosRouteDefinitionSynchronizer synchronizer = synchronizer(
                configService,
                repository,
                new CapturingEventPublisher(),
                true
        );

        assertThatThrownBy(synchronizer::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to initialize Nacos gateway routes");
    }

    private NacosRouteDefinitionSynchronizer synchronizer(
            CapturingConfigService configService,
            NacosRouteDefinitionRepository repository,
            ApplicationEventPublisher eventPublisher,
            boolean failFast) {
        ChaosGatewayNacosRouteProperties properties = new ChaosGatewayNacosRouteProperties();
        properties.setFailFast(failFast);
        return new NacosRouteDefinitionSynchronizer(
                configService,
                properties,
                new NacosRouteDefinitionParser(),
                repository,
                eventPublisher
        );
    }

    private static class CapturingEventPublisher implements ApplicationEventPublisher {

        private int refreshEvents;

        @Override
        public void publishEvent(ApplicationEvent event) {
            if (event instanceof RefreshRoutesEvent) {
                refreshEvents++;
            }
        }

        @Override
        public void publishEvent(Object event) {
            if (event instanceof RefreshRoutesEvent) {
                refreshEvents++;
            }
        }
    }

    private static class CapturingConfigService implements ConfigService {

        private final String content;
        private Listener listener;

        CapturingConfigService(String content) {
            this.content = content;
        }

        @Override
        public String getConfig(String dataId, String group, long timeoutMs) {
            return content;
        }

        @Override
        public String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener) {
            this.listener = listener;
            return content;
        }

        @Override
        public void addListener(String dataId, String group, Listener listener) {
            this.listener = listener;
        }

        @Override
        public boolean publishConfig(String dataId, String group, String content) {
            return false;
        }

        @Override
        public boolean publishConfig(String dataId, String group, String content, String type) {
            return false;
        }

        @Override
        public boolean publishConfigCas(String dataId, String group, String content, String casMd5) {
            return false;
        }

        @Override
        public boolean publishConfigCas(String dataId, String group, String content, String casMd5, String type) {
            return false;
        }

        @Override
        public boolean removeConfig(String dataId, String group) {
            return false;
        }

        @Override
        public void removeListener(String dataId, String group, Listener listener) {
            this.listener = null;
        }

        @Override
        public String getServerStatus() {
            return "UP";
        }

        @Override
        public void addConfigFilter(IConfigFilter configFilter) {
        }

        @Override
        public void shutDown() {
        }

        @Override
        public void fuzzyWatch(String pattern, FuzzyWatchEventWatcher watcher) throws NacosException {
        }

        @Override
        public void fuzzyWatch(String pattern, String group, FuzzyWatchEventWatcher watcher) throws NacosException {
        }

        @Override
        public Future<Set<String>> fuzzyWatchWithGroupKeys(String pattern, FuzzyWatchEventWatcher watcher)
                throws NacosException {
            return null;
        }

        @Override
        public Future<Set<String>> fuzzyWatchWithGroupKeys(
                String pattern,
                String group,
                FuzzyWatchEventWatcher watcher) throws NacosException {
            return null;
        }

        @Override
        public void cancelFuzzyWatch(String pattern, FuzzyWatchEventWatcher watcher) throws NacosException {
        }

        @Override
        public void cancelFuzzyWatch(String pattern, String group, FuzzyWatchEventWatcher watcher)
                throws NacosException {
        }
    }
}
