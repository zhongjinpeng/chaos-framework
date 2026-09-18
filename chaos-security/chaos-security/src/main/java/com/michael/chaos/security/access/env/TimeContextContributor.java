package com.michael.chaos.security.access.env;

import com.michael.chaos.security.api.access.AccessEnvironment;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * 时间环境属性贡献者。
 *
 * <p>写入以下环境属性，供 ABAC 条件引用：</p>
 * <ul>
 *   <li>{@code environment.now} —— ISO-8601 时间戳，可用于 GT/LT 比较；</li>
 *   <li>{@code environment.date} —— 当天日期（yyyy-MM-dd）；</li>
 *   <li>{@code environment.time} —— 当天时刻（HH:mm:ss），配合 BETWEEN 做办公时间限制；</li>
 *   <li>{@code environment.hour} —— 小时（0-23）；</li>
 *   <li>{@code environment.dayOfWeek} —— 星期（MONDAY…SUNDAY）。</li>
 * </ul>
 */
public class TimeContextContributor implements AuthorizationContextContributor {

    private final Clock clock;

    /**
     * 使用系统默认时区创建时间贡献者。
     */
    public TimeContextContributor() {
        this(Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟创建时间贡献者。
     */
    public TimeContextContributor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void contribute(Map<String, Object> environment) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        environment.put(AccessEnvironment.NOW, now.toInstant().toString());
        environment.put(AccessEnvironment.DATE, now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        environment.put(AccessEnvironment.TIME, now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        environment.put(AccessEnvironment.HOUR, now.getHour());
        environment.put(AccessEnvironment.DAY_OF_WEEK, now.getDayOfWeek().name());
    }
}
