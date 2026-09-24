package com.chaos.security.permission;

import com.chaos.core.exception.BizException;
import com.chaos.core.exception.CommonErrorCode;

/**
 * 权限不足异常。
 */
public class PermissionDeniedException extends BizException {

    /**
     * 创建权限不足异常。
     */
    public PermissionDeniedException() {
        super(CommonErrorCode.FORBIDDEN);
    }
}
