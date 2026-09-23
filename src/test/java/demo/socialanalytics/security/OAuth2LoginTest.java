package demo.socialanalytics.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Kiểm tra phần cấu hình Social Login
@SpringBootTest(properties = {
    "social.login.facebook.client-id=test-fb-id",
    "social.login.facebook.client-secret=test-fb-secret",
    "social.login.x.client-id=test-x-id",
    "social.login.x.client-secret=test-x-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginTest {

    @Autowired MockMvc mvc;
    @Autowired ClientRegistrationRepository clientRegistrations;

    private String authorizeRedirect(String registrationId) throws Exception {
        String path = "/oauth2/authorization/" + registrationId;
        return mvc.perform(get("/api/v1" + path).contextPath("/api/v1").servletPath(path))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse().getRedirectedUrl();
    }

    // ----- cấu hình nhà cung cấp -----

    @Test
    void dungEndpointCuaFacebook() {
        ClientRegistration facebook = clientRegistrations.findByRegistrationId("facebook");

        assertThat(facebook).isNotNull();
        assertThat(facebook.getProviderDetails().getAuthorizationUri())
            .contains("facebook.com");
        assertThat(facebook.getScopes()).contains("email", "public_profile");
    }

    // Provider dựng sẵn của Spring Security trỏ vào Graph API v2.8 (bản từ 2016)
    @Test
    void khongBamVaoPhienBanGraphApi() {
        var provider = clientRegistrations.findByRegistrationId("facebook").getProviderDetails();

        assertThat(provider.getAuthorizationUri()).isEqualTo("https://www.facebook.com/dialog/oauth");
        assertThat(provider.getTokenUri()).isEqualTo("https://graph.facebook.com/oauth/access_token");
        assertThat(provider.getAuthorizationUri()).doesNotContain("/v2.8/");
        assertThat(provider.getTokenUri()).doesNotContain("/v2.8/");
    }

    // Phải xin cả ảnh đại diện trong user-info, không tự ghép URL (sẽ ra ảnh silhouette xám).
    @Test
    void xinCaAnhDaiDienTrongUserInfo() {
        var userInfo = clientRegistrations.findByRegistrationId("facebook")
            .getProviderDetails().getUserInfoEndpoint();

        assertThat(userInfo.getUri()).contains("picture.type(large)");
        assertThat(userInfo.getUri()).contains("email");
        assertThat(userInfo.getUserNameAttributeName()).isEqualTo("id");
    }

    // Twitter nay là X: hằng dựng sẵn của Spring Security trỏ sang x.com.
    @Test
    void dungEndpointCuaX() {
        ClientRegistration x = clientRegistrations.findByRegistrationId("x");

        assertThat(x).isNotNull();
        assertThat(x.getProviderDetails().getAuthorizationUri()).contains("x.com");
        assertThat(x.getProviderDetails().getUserInfoEndpoint().getUri())
            .isEqualTo("https://api.x.com/2/users/me");
        assertThat(x.getScopes()).contains("users.read", "tweet.read");
    }

    @Test
    void redirectUriNamDuoiContextPathCuaUngDung() {
        assertThat(clientRegistrations.findByRegistrationId("facebook").getRedirectUri())
            .isEqualTo("{baseUrl}/{action}/oauth2/code/{registrationId}");
    }

    // ----- luồng chuyển hướng đi đăng nhập -----

    @Test
    void chuyenHuongSangFacebook() throws Exception {
        String redirect = authorizeRedirect("facebook");

        assertThat(redirect).startsWith("https://www.facebook.com/");
        assertThat(redirect).contains("client_id=test-fb-id");
        assertThat(redirect).contains("response_type=code");
        assertThat(redirect).contains("state=");
    }

    @Test
    void chuyenHuongSangX() throws Exception {
        String redirect = authorizeRedirect("x");

        assertThat(redirect).startsWith("https://x.com/i/oauth2/authorize");
        assertThat(redirect).contains("client_id=test-x-id");
        assertThat(redirect).contains("scope=users.read");
    }

    // Chạy trên cổng thật để đi qua chuỗi filter của Spring Security
    @Test
    void batPkceChoX() throws Exception {
        String redirect = authorizeRedirect("x");

        assertThat(redirect).contains("code_challenge=");
        assertThat(redirect).contains("code_challenge_method=S256");
    }

    @Test
    void batPkceChoCaFacebook() throws Exception {
        assertThat(authorizeRedirect("facebook")).contains("code_challenge_method=S256");
    }

    // Gõ nhầm registrationId thì KHÔNG chuyển hướng đi đâu cả.
    @Test
    void nhaCungCapChuaKhaiBaoThiKhongChuyenHuongDiDau() throws Exception {
        String path = "/oauth2/authorization/google";

        mvc.perform(get("/api/v1" + path).contextPath("/api/v1").servletPath(path))
            .andExpect(status().isInternalServerError());
    }

    // ----- trang đăng nhập -----

    @Test
    void trangDangNhapHienNutCuaCaHaiNhaCungCap() throws Exception {
        mvc.perform(get("/api/v1/login").contextPath("/api/v1").servletPath("/login")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "/oauth2/authorization/facebook")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "/oauth2/authorization/x")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("X (Twitter)")));
    }
}
