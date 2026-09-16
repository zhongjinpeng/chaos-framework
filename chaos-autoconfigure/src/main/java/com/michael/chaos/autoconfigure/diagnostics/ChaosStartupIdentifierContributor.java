package com.michael.chaos.autoconfigure.diagnostics;

import java.util.Map;

/**
 * 启动标识贡献者。
 *
 * <p>启动报告头默认只有应用名、profile、生产模式和 fail-fast。排查线上问题时经常还需要知道
 * "这是哪个版本、哪个实例、哪个机房"——这些值要么来自构建产物（版本号、构建号），要么只有运行期才知道
 * （容器 hostname、Pod 名、可用区）。静态值用 {@code chaos.diagnostics.startup-report.identifiers}
 * 直接配；动态值注册本接口的 Bean。</p>
 *
 * <p>多个 Bean 的结果按 Bean 顺序合并，同名 key 后者覆盖前者；配置里的静态值优先级最高，
 * 便于用配置临时覆盖某个动态标识而不改代码。</p>
 *
 * <p>实现要求：在启动报告渲染和 {@code /actuator/chaos} 每次访问时同步调用，因此不得阻塞、不得抛异常
 * （抛异常会被捕获并记 debug 日志，该贡献者的标识会被整体丢弃）。返回值应是低频变化的标识，
 * 不要放入随请求变化的内容。</p>
 */
@FunctionalInterface
public interface ChaosStartupIdentifierContributor {

    /**
     * 返回要追加到启动报告头的标识。
     *
     * @return 标识键值对；返回 {@code null} 或空 Map 表示不贡献
     */
    Map<String, String> identifiers();
}
