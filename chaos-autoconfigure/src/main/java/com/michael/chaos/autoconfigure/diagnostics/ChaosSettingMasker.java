package com.michael.chaos.autoconfigure.diagnostics;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * 启动报告配置值脱敏。
 *
 * <p>启动报告会写进日志、也会通过 actuator 端点返回，不能出现任何凭据。这里做两层处理：</p>
 * <ol>
 *     <li>键名命中 secret/password/credential/private-key/access-key/api-key/token 结尾等敏感词时整体掩码；</li>
 *     <li>URL 只保留 scheme、host、port，去掉 userinfo、路径和查询参数（常见的凭据藏身处）。</li>
 * </ol>
 */
public final class ChaosSettingMasker {

    static final String MASK = "******";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(secret|password|passwd|pwd|credential|private[-_.]?key|access[-_.]?key|api[-_.]?key|token)$");

    private ChaosSettingMasker() {
    }

    /**
     * 按键名脱敏。
     *
     * @param key 配置键或展示键
     * @param value 原始值
     * @return 可以安全展示的值
     */
    public static String mask(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return key != null && SENSITIVE_KEY.matcher(key).matches() ? MASK : value;
    }

    /**
     * 把 URL 缩减为 {@code scheme://host[:port]}，无法解析或不含 host 时返回掩码。
     */
    public static String endpoint(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                return MASK;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException ex) {
            return MASK;
        }
    }
}
