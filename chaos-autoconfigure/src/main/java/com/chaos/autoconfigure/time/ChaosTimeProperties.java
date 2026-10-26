package com.chaos.autoconfigure.time;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 应用时间配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.time")
public class ChaosTimeProperties {

    /**
     * 业务日期和本地日期时间使用的时区，默认中国标准时间。
     */
    @NotNull(message = "chaos.time.zone-id must not be null")
    private ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }
}
