package demo.socialanalytics.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import demo.socialanalytics.repository.AuthorizedClientJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

// Chỉ provider nào CÓ ĐỦ credential mới được đăng ký.
//
// Trước đây phần này khai trong spring.security.oauth2.client.registration.* với mặc định
// `${FACEBOOK_CLIENT_ID:#{null}}`. @ConfigurationProperties KHÔNG đánh giá SpEL nên `#{null}`
// bị dùng làm client id nguyên văn: trang đăng nhập hiện đủ hai nút kể cả khi chưa cấu hình gì,
// bấm vào thì tới thẳng trang lỗi của Facebook. Đây là bộ test giữ cho lỗi đó không quay lại.
class SocialLoginClientRegistrationsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
        // Cấu hình này còn khai một bean lưu token cần repository JPA; lát cắt ở đây không dựng
        // JPA nên thay bằng mock.
        .withBean(AuthorizedClientJpaRepository.class,
            () -> org.mockito.Mockito.mock(AuthorizedClientJpaRepository.class))
        .withUserConfiguration(SocialLoginClientRegistrations.class);

    private java.util.List<String> registrationIds(ClientRegistrationRepository repository) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ((InMemoryClientRegistrationRepository) repository)
            .forEach(registration -> ids.add(registration.getRegistrationId()));
        return ids;
    }

    @Test
    void khongCauHinhGiThiKhongCoBeanNaoCa() {
        runner.run(context ->
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
    }

    // Giá trị rỗng cũng phải coi như chưa cấu hình.
    @Test
    void giaTriRongCungCoiNhuChuaCauHinh() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=",
                "social.login.facebook.client-secret=")
            .run(context -> assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class));
    }

    @Test
    void chiCauHinhFacebookThiChiDangKyFacebook() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=fb-id",
                "social.login.facebook.client-secret=fb-secret")
            .run(context -> {
                assertThat(context).hasSingleBean(ClientRegistrationRepository.class);
                assertThat(registrationIds(context.getBean(ClientRegistrationRepository.class)))
                    .containsExactly("facebook");
            });
    }

    @Test
    void cauHinhCaHaiThiDangKyCaHai() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=fb-id",
                "social.login.facebook.client-secret=fb-secret",
                "social.login.x.client-id=x-id",
                "social.login.x.client-secret=x-secret")
            .run(context -> assertThat(
                registrationIds(context.getBean(ClientRegistrationRepository.class)))
                .containsExactlyInAnyOrder("facebook", "x"));
    }

    // Có client id nhưng quên secret: nói thẳng ra, đừng để người dùng ngồi đoán vì sao
    // nút đăng nhập biến mất.
    @Test
    void thieuSecretThiBaoLoiRoRang() {
        runner.withPropertyValues("social.login.facebook.client-id=fb-id")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
                assertThat(context.getStartupFailure().getCause().getMessage())
                    .contains("client-secret");
            });
    }

    @Test
    void facebookKhongBamVaoMotPhienBanGraphApiCuThe() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=fb-id",
                "social.login.facebook.client-secret=fb-secret")
            .run(context -> {
                var facebook = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("facebook");
                assertThat(facebook.getProviderDetails().getAuthorizationUri())
                    .isEqualTo("https://www.facebook.com/dialog/oauth");
                assertThat(facebook.getProviderDetails().getTokenUri())
                    .doesNotContain("/v2.8/");
                // Xin cả ảnh đại diện trong user-info thay vì tự ghép URL.
                assertThat(facebook.getProviderDetails().getUserInfoEndpoint().getUri())
                    .contains("picture.type(large)");
            });
    }
}
