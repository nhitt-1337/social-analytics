package demo.socialanalytics.config;

import demo.socialanalytics.repository.AuthorizedClientJpaRepository;
import demo.socialanalytics.security.JpaOAuth2AuthorizedClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

// @ConfigurationProperties không đánh giá SpEL nên ${VAR:#{null}} vô tác dụng
@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
@ConditionalOnExpression(
    "!'${social.login.facebook.client-id:}'.isBlank() or !'${social.login.x.client-id:}'.isBlank()")
public class SocialLoginClientRegistrations {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginClientRegistrations.class);

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(SocialLoginProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();

        if (properties.facebook().isConfigured()) {
            registrations.add(facebook(properties.facebook()));
        }
        if (properties.x().isConfigured()) {
            registrations.add(x(properties.x()));
        }
        if (registrations.isEmpty()) {
            // Có client id nhưng thiếu secret thì báo thẳng, đừng để nút đăng nhập biến mất lặng lẽ
            throw new IllegalStateException(
                "Social Login cần CẢ client-id và client-secret. Kiểm tra FACEBOOK_CLIENT_SECRET "
                    + "hoặc X_CLIENT_SECRET.");
        }

        log.info("Social Login đã bật cho: {}",
            registrations.stream().map(ClientRegistration::getRegistrationId).toList());
        return new InMemoryClientRegistrationRepository(registrations);
    }

    // Lưu token xuống DB thay cho bản in-memory mặc định của Spring Security.
    @Bean
    public JpaOAuth2AuthorizedClientService jpaOAuth2AuthorizedClientService(
        AuthorizedClientJpaRepository authorizedClientJpaRepository,
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        return new JpaOAuth2AuthorizedClientService(
            authorizedClientJpaRepository, clientRegistrationRepository);
    }

    // Endpoint không ghi phiên bản: Facebook tự định tuyến, không cũ đi theo thời gian
    private ClientRegistration facebook(SocialLoginProperties.Credentials credentials) {
        return CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
            .clientId(credentials.clientId())
            .clientSecret(credentials.clientSecret())
            .clientName("Facebook")
            .scope(credentials.scopes().toArray(String[]::new))
            .authorizationUri("https://www.facebook.com/dialog/oauth")
            .tokenUri("https://graph.facebook.com/oauth/access_token")
            // Xin luôn ảnh đại diện.
            .userInfoUri("https://graph.facebook.com/me?fields=id,name,email,picture.type(large)")
            .userNameAttributeName("id")
            .build();
    }

    // Twitter nay là X; hằng dựng sẵn của Spring Security đã trỏ sang x.com nên giữ nguyên.
    private ClientRegistration x(SocialLoginProperties.Credentials credentials) {
        return CommonOAuth2Provider.X.getBuilder("x")
            .clientId(credentials.clientId())
            .clientSecret(credentials.clientSecret())
            .clientName("X (Twitter)")
            .scope(credentials.scopes().toArray(String[]::new))
            .build();
    }
}
