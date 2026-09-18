package com.michael.chaos.test.audit;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.audit.AuditEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把审计事件收进内存的 {@link AuditEventPublisher}，供断言"某个动作是否留下了审计记录"使用。
 *
 * <p>限流拒绝、权限拒绝、登录失败这类行为的可观测性本身就是需求的一部分，测试里需要断言事件的
 * action、principalId、attributes。此前每个测试类都自己写一份一模一样的收集器，现在统一放在这里。</p>
 */
public class CapturingAuditEventPublisher implements AuditEventPublisher {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void publish(AuditEvent event) {
        events.add(event);
    }

    /**
     * 已收集的全部事件，按发布顺序排列。
     */
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    /**
     * 第一个事件；没有事件时返回空。
     */
    public Optional<AuditEvent> firstEvent() {
        return events.isEmpty() ? Optional.empty() : Optional.of(events.getFirst());
    }

    /**
     * 按动作过滤事件。
     */
    public List<AuditEvent> eventsOf(String action) {
        return events.stream().filter(event -> event.action().equals(action)).toList();
    }

    /**
     * 是否一个事件都没有。
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * 清空已收集的事件，便于同一个实例在多个断言阶段间复用。
     */
    public void clear() {
        events.clear();
    }
}
