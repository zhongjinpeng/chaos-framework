package com.chaos.autoconfigure.authorization;

import com.chaos.authorization.core.ChaosAuthorizationProperties;
import com.chaos.authorization.core.RegisteredClientIds;
import com.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import com.chaos.authorization.grant.ChaosGrantAuthenticationHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

/** Builds registered clients from Chaos authorization properties. */
final class AuthorizationRegisteredClients {

    private AuthorizationRegisteredClients() {
    }

    static List<RegisteredClient> configuredOrDefault(
            ChaosAuthorizationProperties properties,
            ObjectProvider<ChaosGrantAuthenticationHandler> grantHandlers) {
        Map<String, ChaosAuthorizationProperties.ClientRegistration> registrations =
                properties.getClient().getRegistrations();
        if (registrations.isEmpty()) {
            return List.of(defaultClient(properties, grantHandlers));
        }
        return registrations.entrySet().stream()
                .map(entry -> configuredClient(entry, properties))
                .toList();
    }

    private static RegisteredClient configuredClient(
            Map.Entry<String, ChaosAuthorizationProperties.ClientRegistration> entry,
            ChaosAuthorizationProperties properties) {
        ChaosAuthorizationProperties.ClientRegistration registration = entry.getValue();
        String clientId = StringUtils.hasText(registration.getClientId())
                ? registration.getClientId().trim()
                : entry.getKey();
        RegisteredClient.Builder builder = RegisteredClient.withId(RegisteredClientIds.stableId(clientId))
                .clientId(clientId)
                .clientSecret(registration.getSecret());
        Arrays.stream(registration.getAuthenticationMethods())
                .map(ClientAuthenticationMethod::new)
                .forEach(builder::clientAuthenticationMethod);
        Arrays.stream(registration.getGrantTypes())
                .map(AuthorizationGrantType::new)
                .forEach(builder::authorizationGrantType);
        Arrays.stream(registration.getScopes()).forEach(builder::scope);
        return builder
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(tokenSettings(properties))
                .build();
    }

    private static RegisteredClient defaultClient(
            ChaosAuthorizationProperties properties,
            ObjectProvider<ChaosGrantAuthenticationHandler> grantHandlers) {
        return RegisteredClient.withId(RegisteredClientIds.stableId(properties.getClient().getId()))
                .clientId(properties.getClient().getId())
                .clientSecret(properties.getClient().getSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .authorizationGrantType(ChaosAuthorizationGrantTypes.SMS_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantTypes(types -> grantHandlers.orderedStream()
                        .map(ChaosGrantAuthenticationHandler::grantType)
                        .forEach(types::add))
                .scopes(scopes -> scopes.addAll(List.of(properties.getClient().getScopes())))
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(tokenSettings(properties))
                .build();
    }

    private static TokenSettings tokenSettings(ChaosAuthorizationProperties properties) {
        OAuth2TokenFormat accessTokenFormat = properties.getToken().getType()
                == ChaosAuthorizationProperties.TokenType.REDIS
                ? OAuth2TokenFormat.REFERENCE
                : OAuth2TokenFormat.SELF_CONTAINED;
        return TokenSettings.builder()
                .accessTokenTimeToLive(properties.getAccessTokenTtl())
                .accessTokenFormat(accessTokenFormat)
                .refreshTokenTimeToLive(properties.getRefreshTokenTtl())
                .reuseRefreshTokens(properties.isReuseRefreshTokens())
                .build();
    }
}
