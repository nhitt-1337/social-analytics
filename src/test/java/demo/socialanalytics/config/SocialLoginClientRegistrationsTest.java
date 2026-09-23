package demo.socialanalytics.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import demo.socialanalytics.repository.AuthorizedClientJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginClientRegistrationsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
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
    void scopeCauHinhDuocQuaProperties() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=fb-id",
                "social.login.facebook.client-secret=fb-secret",
                "social.login.facebook.scopes=public_profile")
            .run(context -> {
                var facebook = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("facebook");
                assertThat(facebook.getScopes()).containsExactly("public_profile");
                assertThat(facebook.getScopes()).doesNotContain("email");
            });
    }

    @Test
    void khongKhaiScopeThiDungMacDinh() {
        runner.withPropertyValues(
                "social.login.facebook.client-id=fb-id",
                "social.login.facebook.client-secret=fb-secret")
            .run(context -> assertThat(context.getBean(ClientRegistrationRepository.class)
                .findByRegistrationId("facebook").getScopes())
                .containsExactlyInAnyOrder("public_profile", "email"));
    }

    @Test
    void facebookKhongBamVaoPhienBan() {
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
                assertThat(facebook.getProviderDetails().getUserInfoEndpoint().getUri())
                    .contains("picture.type(large)");
            });
    }
}
