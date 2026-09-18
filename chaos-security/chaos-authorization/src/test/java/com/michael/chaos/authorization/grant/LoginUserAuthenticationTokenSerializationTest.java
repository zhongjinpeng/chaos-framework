package com.michael.chaos.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.LoginUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 认证主体的序列化契约测试。
 *
 * <p>认证对象随 {@code OAuth2Authorization} 一起以 JDK 序列化写入 Redis，serialVersionUID 就是存储契约。
 * 不钉死时它由类结构推导，改个字段、加个方法都会让存量令牌读不回来
 * （{@code InvalidClassException: local class incompatible}）。本测试守住这个常量。</p>
 */
class LoginUserAuthenticationTokenSerializationTest {

    /**
     * 认证对象与主体的 serialVersionUID 必须保持钉死的值。
     */
    @Test
    void shouldPinSerialVersionUid() {
        assertThat(ObjectStreamClass.lookup(LoginUserAuthenticationToken.class).getSerialVersionUID())
                .as("改动本类不得让存量令牌失效；确需破坏兼容时请连同 Redis 键前缀一起升版本")
                .isEqualTo(1L);
        assertThat(ObjectStreamClass.lookup(LoginUser.class).getSerialVersionUID())
                .as("LoginUser 是认证主体，同样被写进令牌")
                .isEqualTo(1L);
    }

    /**
     * 认证对象序列化后应能完整还原主体、权限与认证状态。
     */
    @Test
    void shouldRoundTripThroughJavaSerialization() throws Exception {
        LoginUser user = new LoginUser("1001", "michael", "t-1", Set.of("admin"), Set.of("sys:user:read"));
        LoginUserAuthenticationToken token = new LoginUserAuthenticationToken(user);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(token);
        }
        LoginUserAuthenticationToken restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (LoginUserAuthenticationToken) in.readObject();
        }

        assertThat(restored.getPrincipal()).isEqualTo(user);
        assertThat(restored.getName()).isEqualTo("1001");
        assertThat(restored.isAuthenticated()).isTrue();
        assertThat(restored.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_admin", "sys:user:read");
    }
}
