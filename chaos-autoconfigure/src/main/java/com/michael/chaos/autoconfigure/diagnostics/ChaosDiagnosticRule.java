package com.michael.chaos.autoconfigure.diagnostics;

import java.util.List;

/**
 * 启动诊断规则 SPI。
 *
 * <p>规则只读取环境与容器状态并返回提示，不得修改容器、访问外部系统或抛出异常（抛出的异常会被记录并忽略，
 * 诊断不能影响应用启动）。业务方可以注册自己的规则 Bean，结果会出现在启动报告与 {@code /actuator/chaos} 中。</p>
 */
@FunctionalInterface
public interface ChaosDiagnosticRule {

    /**
     * 执行诊断。
     *
     * @param context 诊断上下文
     * @return 诊断提示；没有问题时返回空列表
     */
    List<ChaosFeatureReport.Finding> evaluate(ChaosDiagnosticContext context);
}
