package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 授权请求 / 资源构造器与环境属性组合测试。
 */
class AuthorizationRequestBuilderTest {

    /**
     * 构造器应写入资源、资源属性与环境属性，并忽略空值。
     */
    @Test
    void shouldBuildRequestWithResourceAndEnvironment() {
        AuthorizationRequest request = AuthorizationRequest
                .builder(AccessSubject.ANONYMOUS, "order:update")
                .resource(AuthorizationResource.builder("order")
                        .id("o-1")
                        .attribute("ownerId", "1001")
                        .attribute("blank", null)
                        .attributes(Map.of("amount", 500))
                        .build())
                .environmentAttribute("network", "internal")
                .environmentAttribute("skipped", null)
                .build();

        assertThat(request.action()).isEqualTo("order:update");
        assertThat(request.resource().type()).isEqualTo("order");
        assertThat(request.resource().id()).isEqualTo("o-1");
        assertThat(request.resource().attributes())
                .containsEntry("ownerId", "1001")
                .containsEntry("amount", 500)
                .doesNotContainKey("blank");
        assertThat(request.environment()).containsExactly(Map.entry("network", "internal"));
    }

    /**
     * 资源没有任何内容时应退化为 NONE。
     */
    @Test
    void emptyResourceShouldBeNone() {
        assertThat(AuthorizationResource.builder(" ").build()).isSameAs(AuthorizationResource.NONE);
    }

    /**
     * 组合贡献者应按顺序执行，后者覆盖前者。
     */
    @Test
    void compositeContributorShouldApplyInOrder() {
        CompositeAuthorizationContextContributor contributor = new CompositeAuthorizationContextContributor(List.of(
                environment -> {
                    environment.put("hour", 9);
                    environment.put("network", "internal");
                },
                environment -> environment.put("hour", 22)));

        AuthorizationRequest request = AuthorizationRequest
                .builder(AccessSubject.ANONYMOUS, "order:read")
                .contribute(contributor)
                .contribute(null)
                .build();

        assertThat(contributor.size()).isEqualTo(2);
        assertThat(request.environment())
                .containsEntry("hour", 22)
                .containsEntry("network", "internal");
    }

    /**
     * 空贡献者列表不应影响请求。
     */
    @Test
    void emptyCompositeContributorShouldContributeNothing() {
        AuthorizationRequest request = AuthorizationRequest
                .builder(AccessSubject.ANONYMOUS, "order:read")
                .contribute(new CompositeAuthorizationContextContributor(null))
                .build();

        assertThat(request.environment()).isEmpty();
    }
}
