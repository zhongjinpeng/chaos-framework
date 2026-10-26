package com.chaos.service.transaction;

import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务标记工具，用于“继续返回结果但撤销已完成的写入”这类导入场景。
 *
 * <p>批量导入需要把行级错误汇总成错误文件返回给用户，同时又必须保证整批数据不落库。
 * 这类场景不能靠抛异常中断，因为调用方还要组装附件响应，因此统一在此标记回滚。</p>
 */
public final class TransactionMarks {

    private TransactionMarks() {}

    /**
     * 把当前事务标记为只回滚，已完成的写入在提交阶段全部撤销。
     *
     * <p>没有活动事务时不做任何处理，便于在单元测试或非事务调用中复用同一段导入逻辑。</p>
     */
    public static void markRollbackOnly() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
