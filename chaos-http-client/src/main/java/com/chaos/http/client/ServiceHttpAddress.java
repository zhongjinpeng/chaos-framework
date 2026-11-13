package com.chaos.http.client;

import java.net.URI;
import java.util.Set;

/**
 * 合并服务根地址与接口路径，保留部署前缀并禁止路径覆盖目标主机。
 */
public final class ServiceHttpAddress {

    private ServiceHttpAddress() {
    }

    /**
     * 解析服务间调用地址，不接受认证信息、查询串或片段。
     *
     * @param baseUrl HTTP/HTTPS 根地址，可包含部署路径和末尾斜杠
     * @param path 以单个斜杠开始的接口路径，不包含查询参数或上级目录跳转
     * @return 保留根地址部署前缀的绝对 URI
     * @throws IllegalArgumentException 根地址或路径不符合受控 HTTP 地址约束时抛出
     */
    public static URI resolve(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank() || path == null || path.isBlank()) {
            throw new IllegalArgumentException("服务地址和接口路径不能为空");
        }
        URI base = URI.create(baseUrl);
        URI relative = URI.create(path);
        if (base.getScheme() == null
                || !Set.of("http", "https").contains(base.getScheme())
                || base.getHost() == null
                || base.getRawUserInfo() != null
                || base.getRawQuery() != null
                || base.getRawFragment() != null
                || !path.startsWith("/")
                || path.startsWith("//")
                || relative.isAbsolute()
                || relative.getRawAuthority() != null
                || relative.getRawQuery() != null
                || relative.getRawFragment() != null) {
            throw new IllegalArgumentException("服务地址或接口路径无效");
        }
        for (String segment : (base.getPath() + relative.getPath()).split("/")) {
            if (segment.equals(".") || segment.equals("..") || segment.contains("\\")) {
                throw new IllegalArgumentException("服务地址或接口路径不能包含目录跳转");
            }
        }
        return URI.create(baseUrl.replaceAll("/+$", "") + path);
    }
}
