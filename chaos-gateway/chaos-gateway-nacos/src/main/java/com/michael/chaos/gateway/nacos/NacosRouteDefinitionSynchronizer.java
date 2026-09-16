package com.michael.chaos.gateway.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.StringUtils;

/**
 * 从 Nacos 同步 Gateway 路由并触发 Gateway 路由刷新。
 */
public class NacosRouteDefinitionSynchronizer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NacosRouteDefinitionSynchronizer.class);

    private final ConfigService configService;
    private final ChaosGatewayNacosRouteProperties properties;
    private final NacosRouteDefinitionParser parser;
    private final NacosRouteDefinitionRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Listener listener = new RouteConfigListener();
    private volatile boolean running;

    public NacosRouteDefinitionSynchronizer(
            ConfigService configService,
            ChaosGatewayNacosRouteProperties properties,
            NacosRouteDefinitionParser parser,
            NacosRouteDefinitionRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.configService = configService;
        this.properties = properties;
        this.parser = parser;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            String content = configService.getConfig(
                    properties.getDataId(),
                    properties.getGroup(),
                    properties.getTimeoutMs()
            );
            refresh(content, true);
            configService.addListener(properties.getDataId(), properties.getGroup(), listener);
            running = true;
        } catch (Exception ex) {
            if (properties.isFailFast()) {
                throw new IllegalStateException("Failed to initialize Nacos gateway routes", ex);
            }
            log.warn("Failed to initialize Nacos gateway routes, keep current route table", ex);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        configService.removeListener(properties.getDataId(), properties.getGroup(), listener);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void refresh(String content, boolean initialLoad) {
        if (!StringUtils.hasText(content) && !properties.isClearOnEmpty()) {
            if (initialLoad && properties.isFailFast()) {
                throw new IllegalStateException("Nacos gateway route config must not be empty");
            }
            log.info("Nacos gateway route config is empty, keep current route table");
            return;
        }
        try {
            List<RouteDefinition> routeDefinitions = parser.parse(content);
            repository.replaceRoutes(routeDefinitions);
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Nacos gateway routes refreshed, dataId={}, group={}, routes={}",
                    properties.getDataId(), properties.getGroup(), routeDefinitions.size());
        } catch (Exception ex) {
            if (initialLoad && properties.isFailFast()) {
                throw new IllegalStateException("Failed to refresh Nacos gateway routes", ex);
            }
            log.warn("Failed to refresh Nacos gateway routes, keep last valid route table, reason={}", ex.getMessage());
            log.debug("Nacos gateway route refresh failure details", ex);
        }
    }

    private class RouteConfigListener implements Listener {

        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            refresh(configInfo, false);
        }
    }
}
