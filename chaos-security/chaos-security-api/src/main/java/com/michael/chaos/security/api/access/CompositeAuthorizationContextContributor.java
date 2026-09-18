package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把多个环境属性贡献者合并成一个（组合模式）。
 *
 * <p>按顺序执行，后面的贡献者可以覆盖前面写入的属性；调用方只依赖单个
 * {@link AuthorizationContextContributor}，不必关心实际有几个。</p>
 */
public class CompositeAuthorizationContextContributor implements AuthorizationContextContributor {

    private final List<AuthorizationContextContributor> contributors;

    /**
     * 创建组合贡献者。
     */
    public CompositeAuthorizationContextContributor(List<AuthorizationContextContributor> contributors) {
        this.contributors = contributors == null
                ? List.of()
                : contributors.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public void contribute(Map<String, Object> environment) {
        for (AuthorizationContextContributor contributor : contributors) {
            contributor.contribute(environment);
        }
    }

    /**
     * 贡献者数量。
     */
    public int size() {
        return contributors.size();
    }
}
