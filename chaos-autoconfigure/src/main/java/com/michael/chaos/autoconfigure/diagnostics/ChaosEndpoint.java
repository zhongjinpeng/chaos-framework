package com.michael.chaos.autoconfigure.diagnostics;

import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * {@code /actuator/chaos}：以 JSON 返回与启动日志相同的 Chaos 启动报告。
 *
 * <p>默认访问级别为只读，并且与其他 actuator 端点一样默认不通过 HTTP 暴露，需要显式加入
 * {@code management.endpoints.web.exposure.include}。报告中的配置值已脱敏，但仍会暴露功能组成、
 * 授权服务器地址和实现类名，生产环境应只在管理端口或内网开放，并纳入 actuator 的访问控制。</p>
 */
@Endpoint(id = "chaos", defaultAccess = Access.READ_ONLY)
public class ChaosEndpoint {

    private final ChaosFeatureReporter reporter;

    /**
     * 创建端点。
     */
    public ChaosEndpoint(ChaosFeatureReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * 返回当前 Chaos 启动报告。
     */
    @ReadOperation
    public ChaosFeatureReport report() {
        return reporter.build();
    }
}
