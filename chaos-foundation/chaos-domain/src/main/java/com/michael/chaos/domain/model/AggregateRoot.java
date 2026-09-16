package com.michael.chaos.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DDD 聚合根基类。
 *
 * <p>聚合根负责维护自身产生的领域事件，应用服务在事务提交后可以读取并发布这些事件。</p>
 *
 * @param <ID> 聚合根标识类型
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 返回聚合根唯一标识。
     */
    public abstract ID id();

    /**
     * 注册领域事件。
     *
     * @param event 聚合内产生的领域事件
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * 返回当前聚合尚未发布的领域事件。
     */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清理已发布的领域事件。
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
