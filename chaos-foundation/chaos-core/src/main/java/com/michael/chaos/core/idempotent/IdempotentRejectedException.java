package com.michael.chaos.core.idempotent;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;

/**
 * 幂等校验拒绝异常。
 */
public class IdempotentRejectedException extends BizException {

    /**
     * 创建幂等拒绝异常。
     */
    public IdempotentRejectedException() {
        super(CommonErrorCode.IDEMPOTENT_REJECTED);
    }
}
