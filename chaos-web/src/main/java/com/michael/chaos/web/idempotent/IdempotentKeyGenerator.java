package com.michael.chaos.web.idempotent;

/**
 * 幂等 key 生成策略。
 */
public interface IdempotentKeyGenerator {

    /**
     * 根据上下文生成幂等 key。
     *
     * @param context 幂等上下文
     * @return 幂等 key
     */
    String generate(IdempotentKeyContext context);
}
