package com.chaos.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 服务地址拼接与安全校验测试。
 */
class ServiceHttpAddressTest {

    /**
     * 保留部署前缀并消除末尾斜杠。
     */
    @Test
    void shouldPreserveDeploymentPrefixAndNormalizeTrailingSlash() {
        for (String base : new String[] {"https://lims.invalid/internal", "https://lims.invalid/internal///"}) {
            assertEquals(
                    "https://lims.invalid/internal/api/factors/options",
                    ServiceHttpAddress.resolve(base, "/api/factors/options").toString());
        }
    }

    /**
     * 拒绝凭据、查询串、片段和不受支持的 scheme。
     */
    @Test
    void shouldRejectCredentialQueryFragmentOrUnsupportedOrigin() {
        for (String base : new String[] {
                "/relative",
                "file:///tmp",
                "https://user:secret@lims.invalid",
                "https://lims.invalid?target=x",
                "https://lims.invalid#fragment",
                "",
                "https://lims.invalid/a/../b"
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ServiceHttpAddress.resolve(base, "/api/test"),
                    base);
        }
    }

    /**
     * 拒绝可能改变目标主机或跳出部署前缀的路径。
     */
    @Test
    void shouldRejectPathsThatCouldChangeAuthorityOrEscapePrefix() {
        for (String path : new String[] {
                "//other.invalid/api",
                "https://other.invalid/api",
                "api/test",
                "/../api",
                "/%2e%2e/api",
                "/api?token=x",
                "/api#fragment",
                "/%5cother",
                ""
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ServiceHttpAddress.resolve("https://lims.invalid/internal", path),
                    path);
        }
    }
}
