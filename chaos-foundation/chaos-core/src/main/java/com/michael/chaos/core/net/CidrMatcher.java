package com.michael.chaos.core.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IP / CIDR 白名单匹配器。
 *
 * <p>用于判断请求的直连对端是否为可信代理（网关、负载均衡）。只有来自可信代理的
 * {@code X-Forwarded-For} 和身份透传头才能被信任，否则任何客户端都能伪造 IP 绕过黑名单与限流，
 * 或伪造用户/租户身份。</p>
 *
 * <p>支持 IPv4、IPv6 单地址及 CIDR 表示法，例如 {@code 10.0.0.0/8}、{@code 192.168.1.10}、
 * {@code fd00::/8}。解析时只接受字面量 IP，不做 DNS 解析，避免配置项触发网络请求。</p>
 */
public final class CidrMatcher {

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final List<Range> ranges;

    private CidrMatcher(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    /**
     * 根据 CIDR 列表创建匹配器。
     *
     * @param cidrs IP 或 CIDR 列表，空白项会被忽略
     * @return 匹配器
     * @throws IllegalArgumentException 存在无法解析的条目时抛出，保证配置错误在启动期暴露
     */
    public static CidrMatcher of(Collection<String> cidrs) {
        List<Range> ranges = new ArrayList<>();
        if (cidrs != null) {
            for (String cidr : cidrs) {
                if (cidr == null || cidr.isBlank()) {
                    continue;
                }
                ranges.add(Range.parse(cidr.trim()));
            }
        }
        return new CidrMatcher(ranges);
    }

    /**
     * 根据 CIDR 列表创建匹配器，跳过无法解析的条目。
     *
     * <p>用于运行期可动态刷新的配置（如 Nacos 下发的网关可信代理、黑名单）：单条错误配置不应让每个请求都抛异常。
     * 启动期能校验的配置应优先使用 {@link #of(Collection)}。</p>
     *
     * @param cidrs IP 或 CIDR 列表
     * @return 匹配器
     */
    public static CidrMatcher lenient(Collection<String> cidrs) {
        List<Range> ranges = new ArrayList<>();
        if (cidrs != null) {
            for (String cidr : cidrs) {
                if (cidr == null || cidr.isBlank()) {
                    continue;
                }
                try {
                    ranges.add(Range.parse(cidr.trim()));
                } catch (IllegalArgumentException ex) {
                    // 跳过非法条目，保留其余合法规则。
                }
            }
        }
        return new CidrMatcher(ranges);
    }

    /**
     * 创建不匹配任何地址的匹配器。
     */
    public static CidrMatcher none() {
        return new CidrMatcher(List.of());
    }

    /**
     * 是否未配置任何条目。
     */
    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    /**
     * 判断地址是否命中任一条目。
     *
     * @param address 字面量 IP 地址；非法地址返回 {@code false}
     * @return 命中返回 {@code true}
     */
    public boolean matches(String address) {
        if (ranges.isEmpty()) {
            return false;
        }
        byte[] bytes = parseLiteral(address);
        if (bytes == null) {
            return false;
        }
        for (Range range : ranges) {
            if (range.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断地址是否命中任一规则，忽略无法解析的规则。
     *
     * <p>用于黑名单等运行期可动态刷新的规则列表：单条错误配置不应让整个过滤器抛异常，
     * 与 {@link #of(Collection)} 的启动期严格校验区分开。</p>
     *
     * @param address 字面量 IP 地址
     * @param rules IP 或 CIDR 规则
     * @return 命中任一合法规则返回 {@code true}
     */
    public static boolean matchesAny(String address, Collection<String> rules) {
        return lenient(rules).matches(address);
    }

    /**
     * 判断字符串是否为合法的字面量 IP 地址。
     */
    public static boolean isIpLiteral(String address) {
        return parseLiteral(address) != null;
    }

    /**
     * 解析字面量 IP，拒绝主机名以避免 DNS 查询。
     */
    static byte[] parseLiteral(String address) {
        if (address == null) {
            return null;
        }
        String value = address.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zoneIndex = value.indexOf('%');
        if (zoneIndex >= 0) {
            value = value.substring(0, zoneIndex);
        }
        if (value.isEmpty()) {
            return null;
        }
        boolean ipv4 = IPV4_LITERAL.matcher(value).matches();
        boolean ipv6 = !ipv4 && value.indexOf(':') >= 0 && IPV6_LITERAL.matcher(value).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        if (ipv4) {
            for (String part : value.split("\\.")) {
                if (Integer.parseInt(part) > 255) {
                    return null;
                }
            }
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private record Range(byte[] network, int prefixLength) {

        static Range parse(String cidr) {
            int slash = cidr.indexOf('/');
            String addressPart = slash < 0 ? cidr : cidr.substring(0, slash);
            byte[] network = parseLiteral(addressPart);
            if (network == null) {
                throw new IllegalArgumentException("Illegal IP/CIDR: " + cidr);
            }
            int maxPrefix = network.length * 8;
            int prefix = maxPrefix;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(cidr.substring(slash + 1));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Illegal CIDR prefix: " + cidr, ex);
                }
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("Illegal CIDR prefix: " + cidr);
                }
            }
            return new Range(network, prefix);
        }

        boolean contains(byte[] address) {
            byte[] candidate = address;
            if (candidate.length != network.length) {
                candidate = mapIpv4(candidate, network.length);
                if (candidate == null) {
                    return false;
                }
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        /**
         * 兼容 IPv4-mapped IPv6（::ffff:a.b.c.d）与 IPv4 规则互相匹配。
         */
        private static byte[] mapIpv4(byte[] address, int targetLength) {
            if (address.length == 16 && targetLength == 4) {
                for (int i = 0; i < 10; i++) {
                    if (address[i] != 0) {
                        return null;
                    }
                }
                if (address[10] != (byte) 0xFF || address[11] != (byte) 0xFF) {
                    return null;
                }
                return new byte[]{address[12], address[13], address[14], address[15]};
            }
            if (address.length == 4 && targetLength == 16) {
                byte[] mapped = new byte[16];
                mapped[10] = (byte) 0xFF;
                mapped[11] = (byte) 0xFF;
                System.arraycopy(address, 0, mapped, 12, 4);
                return mapped;
            }
            return null;
        }
    }
}
