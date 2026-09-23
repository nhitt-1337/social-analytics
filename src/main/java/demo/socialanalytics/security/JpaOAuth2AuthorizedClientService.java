package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthorizedClient;
import demo.socialanalytics.repository.AuthorizedClientJpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class JpaOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {
    private final AuthorizedClientJpaRepository authorizedClientRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public JpaOAuth2AuthorizedClientService(
        AuthorizedClientJpaRepository authorizedClientRepository,
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
        String clientRegistrationId, String principalName) {
        ClientRegistration registration =
            clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
        if (registration == null) {
            return null;
        }
        return (T) authorizedClientRepository
            .findByRegistrationIdAndPrincipalName(clientRegistrationId, principalName)
            .map(stored -> toAuthorizedClient(registration, stored))
            .orElse(null);
    }

    @Override
    @Transactional
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        String registrationId = authorizedClient.getClientRegistration().getRegistrationId();
        String principalName = principal.getName();

        AuthorizedClient stored = authorizedClientRepository
            .findByRegistrationIdAndPrincipalName(registrationId, principalName)
            .orElseGet(AuthorizedClient::new);

        stored.setRegistrationId(registrationId);
        stored.setPrincipalName(principalName);

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        stored.setAccessTokenType(accessToken.getTokenType().getValue());
        stored.setAccessTokenValue(accessToken.getTokenValue());
        stored.setAccessTokenIssuedAt(accessToken.getIssuedAt());
        stored.setAccessTokenExpiresAt(accessToken.getExpiresAt());
        stored.setAccessTokenScopes(
            accessToken.getScopes() == null ? null : String.join(",", accessToken.getScopes()));

        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
        if (refreshToken != null) {
            stored.setRefreshTokenValue(refreshToken.getTokenValue());
            stored.setRefreshTokenIssuedAt(refreshToken.getIssuedAt());
        }

        authorizedClientRepository.save(stored);
    }

    @Override
    @Transactional
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        authorizedClientRepository
            .deleteByRegistrationIdAndPrincipalName(clientRegistrationId, principalName);
    }

    private OAuth2AuthorizedClient toAuthorizedClient(
        ClientRegistration registration, AuthorizedClient stored) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            stored.getAccessTokenValue(),
            stored.getAccessTokenIssuedAt(),
            stored.getAccessTokenExpiresAt(),
            parseScopes(stored.getAccessTokenScopes()));

        OAuth2RefreshToken refreshToken = stored.getRefreshTokenValue() == null
            ? null
            : new OAuth2RefreshToken(stored.getRefreshTokenValue(), stored.getRefreshTokenIssuedAt());

        return new OAuth2AuthorizedClient(
            registration, stored.getPrincipalName(), accessToken, refreshToken);
    }

    private Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(scopes.split(",")));
    }
}
