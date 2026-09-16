package com.michael.chaos.autoconfigure.authorization;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.ClassUtils;

/**
 * 授权服务器 JDBC 存储的创建入口。
 *
 * <p>spring-jdbc 是 chaos-autoconfigure 的可选依赖，引用 {@link JdbcOperations} 的代码集中在本类，原因同
 * {@link AuthorizationRedisStores}。</p>
 */
final class AuthorizationJdbcStores {

    private AuthorizationJdbcStores() {
    }

    static boolean isPresent(ClassLoader classLoader) {
        return ClassUtils.isPresent("org.springframework.jdbc.core.JdbcOperations", classLoader);
    }

    static RegisteredClientRepository registeredClientRepository(BeanFactory beanFactory) {
        return new JdbcRegisteredClientRepository(requireJdbcOperations(beanFactory, "JDBC client store mode"));
    }

    static OAuth2AuthorizationService authorizationService(
            BeanFactory beanFactory,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(
                requireJdbcOperations(beanFactory, "JDBC authorization mode"), registeredClientRepository);
    }

    static OAuth2AuthorizationConsentService authorizationConsentService(
            BeanFactory beanFactory,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(
                requireJdbcOperations(beanFactory, "JDBC authorization consent mode"), registeredClientRepository);
    }

    private static JdbcOperations requireJdbcOperations(BeanFactory beanFactory, String mode) {
        JdbcOperations jdbcOperations = beanFactory.getBeanProvider(JdbcOperations.class).getIfAvailable();
        if (jdbcOperations == null) {
            throw new IllegalStateException(mode + " requires a JdbcOperations bean");
        }
        return jdbcOperations;
    }
}
