package demo.socialanalytics.security;

import demo.socialanalytics.repository.AuthorizedClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Token OAuth2 phải sống sót qua lần khởi động lại: bản mặc định của Spring Security chỉ giữ
// trong bộ nhớ. @DataJpaTest để chạy thật trên DB thay vì mock repository — cái cần kiểm ở đây
// chính là việc ghi/đọc có đúng không.
@DataJpaTest
@ActiveProfiles("test")
class JpaOAuth2AuthorizedClientServiceTest {

    @Autowired AuthorizedClientJpaRepository repository;

    JpaOAuth2AuthorizedClientService service;
    ClientRegistration facebook;
    Authentication principal;

    @BeforeEach
    void setUp() {
        facebook = CommonOAuth2Provider.FACEBOOK
            .getBuilder("facebook")
            .clientId("id").clientSecret("secret")
            .build();
        ClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(facebook);
        service = new JpaOAuth2AuthorizedClientService(repository, registrations);
        principal = new UsernamePasswordAuthenticationToken("facebook:123", "n/a", List.of());
    }

    private OAuth2AuthorizedClient authorizedClient(String tokenValue, String refreshValue) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, tokenValue, now, now.plusSeconds(3_600),
            Set.of("email", "public_profile"));
        OAuth2RefreshToken refreshToken = refreshValue == null
            ? null
            : new OAuth2RefreshToken(refreshValue, now);
        return new OAuth2AuthorizedClient(facebook, principal.getName(), accessToken, refreshToken);
    }

    @Test
    void luuRoiDocLaiDuocAccessToken() {
        service.saveAuthorizedClient(authorizedClient("token-abc", "refresh-abc"), principal);

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:123");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("token-abc");
        assertThat(loaded.getAccessToken().getScopes()).containsExactlyInAnyOrder("email", "public_profile");
        assertThat(loaded.getRefreshToken()).isNotNull();
        assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-abc");
        assertThat(loaded.getPrincipalName()).isEqualTo("facebook:123");
        assertThat(loaded.getClientRegistration().getRegistrationId()).isEqualTo("facebook");
    }

    @Test
    void giuNguyenHanSuDungCuaToken() {
        OAuth2AuthorizedClient saved = authorizedClient("token-abc", null);
        service.saveAuthorizedClient(saved, principal);

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:123");

        assertThat(loaded.getAccessToken().getExpiresAt())
            .isEqualTo(saved.getAccessToken().getExpiresAt());
    }

    // Đăng nhập lại phải GHI ĐÈ chứ không tạo thêm dòng mới.
    @Test
    void dangNhapLaiThiGhiDeTokenCu() {
        service.saveAuthorizedClient(authorizedClient("token-cu", "refresh-cu"), principal);
        service.saveAuthorizedClient(authorizedClient("token-moi", "refresh-moi"), principal);

        assertThat(repository.findAll()).hasSize(1);
        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:123");
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("token-moi");
    }

    // Nhà cung cấp thường chỉ gửi refresh token ở lần cấp quyền đầu tiên. Lần sau không có
    // thì phải giữ bản cũ, xoá đi là job nền hết đường làm mới token.
    @Test
    void khongNhanDuocRefreshTokenMoiThiGiuBanCu() {
        service.saveAuthorizedClient(authorizedClient("token-cu", "refresh-cu"), principal);

        service.saveAuthorizedClient(authorizedClient("token-moi", null), principal);

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:123");
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("token-moi");
        assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-cu");
    }

    @Test
    void chuaLuuThiTraVeNull() {
        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:999");
        assertThat(loaded).isNull();
    }

    @Test
    void nhaCungCapKhongCoTrongCauHinhThiTraVeNull() {
        service.saveAuthorizedClient(authorizedClient("token-abc", null), principal);

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("khong-ton-tai", "facebook:123");
        assertThat(loaded).isNull();
    }

    @Test
    void xoaThiKhongDocLaiDuoc() {
        service.saveAuthorizedClient(authorizedClient("token-abc", null), principal);

        service.removeAuthorizedClient("facebook", "facebook:123");

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("facebook", "facebook:123");
        assertThat(loaded).isNull();
        assertThat(repository.findAll()).isEmpty();
    }

    // Hai người dùng khác nhau trên cùng một nhà cung cấp là hai dòng riêng biệt.
    @Test
    void tachBachTokenGiuaCacNguoiDung() {
        Authentication other = new UsernamePasswordAuthenticationToken("facebook:456", "n/a", List.of());
        service.saveAuthorizedClient(authorizedClient("token-123", null), principal);
        service.saveAuthorizedClient(
            new OAuth2AuthorizedClient(facebook, "facebook:456",
                authorizedClient("token-456", null).getAccessToken()), other);

        assertThat(repository.findAll()).hasSize(2);
        OAuth2AuthorizedClient first = service.loadAuthorizedClient("facebook", "facebook:123");
        OAuth2AuthorizedClient second = service.loadAuthorizedClient("facebook", "facebook:456");
        assertThat(first.getAccessToken().getTokenValue()).isEqualTo("token-123");
        assertThat(second.getAccessToken().getTokenValue()).isEqualTo("token-456");
    }
}
