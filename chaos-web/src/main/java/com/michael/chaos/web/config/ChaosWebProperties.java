package com.michael.chaos.web.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Web 模块配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.web")
public class ChaosWebProperties {

    /**
     * 是否启用统一响应包装。
     */
    private boolean responseWrapEnabled = true;

    /**
     * 是否启用 trace 过滤器。
     */
    private boolean traceEnabled = true;

    /**
     * 是否输出请求耗时日志。
     */
    private boolean requestTimingEnabled = true;

    /**
     * 是否启用基础 XSS 参数转义。
     *
     * <p>默认关闭：输入端转义会污染入库数据且无法覆盖 JSON 请求体，推荐在输出端按上下文编码。
     * 仅建议在渲染服务端模板且无法改造输出编码的遗留系统中开启。</p>
     */
    private boolean xssEnabled = false;

    /**
     * XSS 过滤排除路径（Ant 风格），匹配的路径不做 XSS 转义。
     */
    private List<String> xssExcludePaths = new ArrayList<>();

    /**
     * 限流配置。
     */
    @Valid
    @NotNull(message = "chaos.web.rate-limit must not be null")
    private RateLimit rateLimit = new RateLimit();

    /**
     * 幂等配置。
     */
    @Valid
    @NotNull(message = "chaos.web.idempotent must not be null")
    private Idempotent idempotent = new Idempotent();

    /**
     * 代理转发与身份透传信任配置。
     */
    @Valid
    @NotNull(message = "chaos.web.forwarding must not be null")
    private Forwarding forwarding = new Forwarding();

    /**
     * 错误文案国际化配置。
     */
    @Valid
    @NotNull(message = "chaos.web.i18n must not be null")
    private I18n i18n = new I18n();

    public boolean isResponseWrapEnabled() {
        return responseWrapEnabled;
    }

    public void setResponseWrapEnabled(boolean responseWrapEnabled) {
        this.responseWrapEnabled = responseWrapEnabled;
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public boolean isRequestTimingEnabled() {
        return requestTimingEnabled;
    }

    public void setRequestTimingEnabled(boolean requestTimingEnabled) {
        this.requestTimingEnabled = requestTimingEnabled;
    }

    public boolean isXssEnabled() {
        return xssEnabled;
    }

    public void setXssEnabled(boolean xssEnabled) {
        this.xssEnabled = xssEnabled;
    }

    public List<String> getXssExcludePaths() {
        return xssExcludePaths;
    }

    public void setXssExcludePaths(List<String> xssExcludePaths) {
        this.xssExcludePaths = xssExcludePaths == null ? new ArrayList<>() : xssExcludePaths;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit == null ? new RateLimit() : rateLimit;
    }

    public Idempotent getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Idempotent idempotent) {
        this.idempotent = idempotent == null ? new Idempotent() : idempotent;
    }

    public Forwarding getForwarding() {
        return forwarding;
    }

    public void setForwarding(Forwarding forwarding) {
        this.forwarding = forwarding == null ? new Forwarding() : forwarding;
    }

    public I18n getI18n() {
        return i18n;
    }

    public void setI18n(I18n i18n) {
        this.i18n = i18n == null ? new I18n() : i18n;
    }

    /**
     * 错误文案国际化配置。
     */
    public static class I18n {

        /**
         * 是否启用错误文案国际化。
         *
         * <p>关闭后框架直接使用错误码默认消息和内置英文提示，不做任何 {@code MessageSource} 查找。</p>
         */
        private boolean enabled = true;

        /**
         * 固定使用的语言标签，例如 {@code zh-CN}、{@code en}。
         *
         * <p>留空时按请求语言解析（Spring MVC 默认读取 {@code Accept-Language}，客户端未声明时取服务端默认语言）。
         * 只对内部系统、或产品明确只提供单一语言时才需要固定。</p>
         */
        private String defaultLocale = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultLocale() {
            return defaultLocale;
        }

        public void setDefaultLocale(String defaultLocale) {
            this.defaultLocale = defaultLocale == null ? "" : defaultLocale.trim();
        }
    }

    /**
     * 代理转发与身份透传信任配置。
     *
     * <p>默认不信任任何代理：客户端 IP 取 {@code remoteAddr}，{@code X-User-Id}/{@code X-Tenant-Id} 被忽略。</p>
     */
    public static class Forwarding {

        /**
         * 可信代理 IP 或 CIDR 列表，例如网关、Ingress 所在网段 {@code 10.0.0.0/8}。
         *
         * <p>只有请求直连对端命中该列表时，才会解析 {@code X-Forwarded-For} 获取客户端 IP。
         * 显式配置 {@code 0.0.0.0/0} 表示信任所有来源，仅适用于网络层已隔离的内网服务。</p>
         */
        private List<String> trustedProxies = new ArrayList<>();

        /**
         * 是否信任可信代理透传的 {@code X-User-Id}/{@code X-Tenant-Id} 身份请求头。
         *
         * <p>默认 {@code false}。开启后仍要求直连对端命中 {@link #trustedProxies}，
         * 且上游网关必须先删除外部请求携带的同名请求头。</p>
         */
        private boolean trustIdentityHeaders = false;

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
        }

        public boolean isTrustIdentityHeaders() {
            return trustIdentityHeaders;
        }

        public void setTrustIdentityHeaders(boolean trustIdentityHeaders) {
            this.trustIdentityHeaders = trustIdentityHeaders;
        }
    }

    /**
     * Web 限流配置。
     */
    public static class RateLimit {

        /**
         * 是否启用限流拦截器。
         */
        private boolean enabled = true;

        /**
         * 默认每秒许可数，未在 {@code @RateLimit} 指定时生效。
         */
        @Min(value = 1, message = "chaos.web.rate-limit.default-permits-per-second must be positive")
        private int defaultPermitsPerSecond = 100;

        /**
         * 默认内存限流器最多保留的 key 数。
         */
        @Min(value = 1, message = "chaos.web.rate-limit.max-local-keys must be positive")
        private int maxLocalKeys = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultPermitsPerSecond() {
            return defaultPermitsPerSecond;
        }

        public void setDefaultPermitsPerSecond(int defaultPermitsPerSecond) {
            this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        }

        public int getMaxLocalKeys() {
            return maxLocalKeys;
        }

        public void setMaxLocalKeys(int maxLocalKeys) {
            this.maxLocalKeys = maxLocalKeys;
        }
    }

    /**
     * Web 幂等配置。
     */
    public static class Idempotent {

        /**
         * 幂等 key 保留时间。
         */
        @NotNull(message = "chaos.web.idempotent.ttl must not be null")
        private Duration ttl = Duration.ofMinutes(5);

        /**
         * 默认内存幂等仓储最多保留的 key 数。
         */
        @Min(value = 1, message = "chaos.web.idempotent.max-local-keys must be positive")
        private int maxLocalKeys = 10_000;

        /**
         * 重复请求响应回放配置。
         */
        @Valid
        @NotNull(message = "chaos.web.idempotent.replay must not be null")
        private Replay replay = new Replay();

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxLocalKeys() {
            return maxLocalKeys;
        }

        public void setMaxLocalKeys(int maxLocalKeys) {
            this.maxLocalKeys = maxLocalKeys;
        }

        public Replay getReplay() {
            return replay;
        }

        public void setReplay(Replay replay) {
            this.replay = replay == null ? new Replay() : replay;
        }
    }

    /**
     * 重复请求响应回放配置。
     */
    public static class Replay {

        /**
         * 是否回放首次响应。
         *
         * <p>默认关闭，保持"重复请求返回 409"的既有行为。开启后 {@code @Idempotent} 接口的重复请求
         * 会直接返回首次执行的状态码与响应体，并带上 {@code Idempotency-Replayed: true} 响应头。
         * 开启需要付出两项成本：写请求的响应体会被缓存一份用于采集，以及每个幂等请求多一次快照查询。</p>
         */
        private boolean enabled = false;

        /**
         * 单次响应体最大缓存大小，超过则不保存快照，该 key 的重复请求退回 409。
         */
        @NotNull(message = "chaos.web.idempotent.replay.max-body-size must not be null")
        private DataSize maxBodySize = DataSize.ofKilobytes(64);

        /**
         * 需要一并回放的响应头。
         *
         * <p>默认只有 {@code Location}：创建类接口的资源地址在响应头里，不回放就无法与首次响应等价。
         * 其余响应头由本次请求重新生成，回放旧值会产生误导。</p>
         */
        private List<String> storedHeaders = new ArrayList<>(List.of("Location"));

        /**
         * 内存响应快照存储最多保留的条数，仅在没有 Redis 实现时生效。
         */
        @Min(value = 1, message = "chaos.web.idempotent.replay.max-local-records must be positive")
        private int maxLocalRecords = 1_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public DataSize getMaxBodySize() {
            return maxBodySize;
        }

        public void setMaxBodySize(DataSize maxBodySize) {
            this.maxBodySize = maxBodySize;
        }

        public List<String> getStoredHeaders() {
            return storedHeaders;
        }

        public void setStoredHeaders(List<String> storedHeaders) {
            this.storedHeaders = storedHeaders == null ? new ArrayList<>() : storedHeaders;
        }

        public int getMaxLocalRecords() {
            return maxLocalRecords;
        }

        public void setMaxLocalRecords(int maxLocalRecords) {
            this.maxLocalRecords = maxLocalRecords;
        }
    }
}
