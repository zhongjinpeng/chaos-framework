package com.michael.chaos.redis.lua;

import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

/**
 * 基于 Redisson 的 Lua 脚本执行器。
 */
public class LuaScriptExecutor {

    private final RedissonClient redissonClient;

    /**
     * 创建 Lua 脚本执行器。
     */
    public LuaScriptExecutor(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 以读写模式执行 Lua 脚本。
     *
     * @param script Lua 脚本内容
     * @param returnType 返回值类型
     * @param keys Redis key 列表
     * @param values 脚本参数
     * @param <R> 返回值类型
     * @return 脚本执行结果
     */
    public <R> R eval(String script, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return redissonClient.getScript().eval(RScript.Mode.READ_WRITE, script, returnType, keys, values);
    }
}
