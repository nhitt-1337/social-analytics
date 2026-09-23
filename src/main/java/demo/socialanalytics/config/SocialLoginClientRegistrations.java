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

// Dựng danh sách nhà cung cấp Social Login TỪ CODE thay vì khai trong
// spring.security.oauth2.client.registration.*
//
// Lý do: khai trong yaml thì mọi provider viết ở đó luôn được đăng ký, kể cả khi chưa có
// client id. Trang đăng nhập sẽ hiện nút dẫn thẳng tới trang lỗi của Facebook/X.
// (Đặt mặc định `${FACEBOOK_CLIENT_ID:#{null}}` cũng không cứu được: @ConfigurationProperties
// KHÔNG đánh giá SpEL, nên `#{null}` bị dùng làm client id nguyên văn.)
//
// Ở đây chỉ provider nào có đủ client id + secret mới được đăng ký. Không có cái nào thì bean
// này không tồn tại -> SecurityConfig bỏ qua phần oauth2Login và app vẫn chạy bình thường.
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
            // Có client id nhưng thiếu secret -> nói thẳng, đừng để người dùng đoán vì sao
            // nút đăng nhập biến mất.
            throw new IllegalStateException(
                "Social Login cần CẢ client-id và client-secret. Kiểm tra FACEBOOK_CLIENT_SECRET "
                    + "hoặc X_CLIENT_SECRET.");
        }

        log.info("Social Login đã bật cho: {}",
            registrations.stream().map(ClientRegistration::getRegistrationId).toList());
        return new InMemoryClientRegistrationRepository(registrations);
    }

    // Lưu token xuống DB thay cho bản in-memory mặc định của Spring Security.
    // Khai ở đây vì nó cần ClientRegistrationRepository ở trên; chưa cấu hình Social Login thì
    // cả hai cùng không tồn tại và app vẫn chạy bình thường.
    @Bean
    public JpaOAuth2AuthorizedClientService jpaOAuth2AuthorizedClientService(
        AuthorizedClientJpaRepository authorizedClientJpaRepository,
        ClientRegistrationRepository clientRegistrationRepository
    ) {
        return new JpaOAuth2AuthorizedClientService(
            authorizedClientJpaRepository, clientRegistrationRepository);
    }

    // Provider dựng sẵn của Spring Security trỏ vào Graph API v2.8 (bản từ 2016). Facebook hiện
    // vẫn định tuyến được, nhưng bám vào một bản đã bỏ 9 năm là chuyện sớm muộn sẽ hỏng.
    // Dùng endpoint KHÔNG ghi phiên bản: Facebook tự định tuyến sang bản hỗ trợ cũ nhất, nên
    // không có con số nào để cũ đi theo thời gian.
    private ClientRegistration facebook(SocialLoginProperties.Credentials credentials) {
        return CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
            .clientId(credentials.clientId())
            .clientSecret(credentials.clientSecret())
            .clientName("Facebook")
            .scope(credentials.scopes().toArray(String[]::new))
            .authorizationUri("https://www.facebook.com/dialog/oauth")
            .tokenUri("https://graph.facebook.com/oauth/access_token")
            // Xin luôn ảnh đại diện. Tự ghép URL graph.facebook.com/{id}/picture mà không kèm
            // access token thì Facebook trả về ảnh silhouette xám cho mọi người.
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
