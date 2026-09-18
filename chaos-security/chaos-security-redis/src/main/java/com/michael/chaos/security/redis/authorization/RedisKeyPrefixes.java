package com.michael.chaos.security.redis.authorization;

/**
 * 授权服务器 Redis 存储的 key 前缀规范化。
 *
 * <p>四个存储此前各写了一份前缀处理：两份用正则去掉末尾**所有**冒号，另外两份只去掉**一个**，
 * 于是同一份配置（例如误写成 {@code chaos:authorization::}）在不同存储里会落到不同的 key 上。
 * 统一到这里并采用"去掉末尾所有冒号"的宽松写法：使用方在配置里多写一个冒号不该导致数据分裂。</p>
 */
final class RedisKeyPrefixes {

    private RedisKeyPrefixes() {
    }

    /**
     * 规范化 key 前缀。
     *
     * @param value 配置值，为空时使用默认前缀
     * @param defaultPrefix 默认前缀
     * @return 不以冒号结尾的前缀
     */
    static String normalize(String value, String defaultPrefix) {
        String prefix = value == null || value.isBlank() ? defaultPrefix : value.trim();
        return prefix.replaceAll(":+$", "");
    }
}
