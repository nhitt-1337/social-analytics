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

// Kiểm tra phần cấu hình Social Login. Đặt client id/secret giả qua properties: đủ để Spring Boot
// tạo ClientRegistration và dựng URL chuyển hướng, không hề gọi ra Internet.
@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.facebook.client-id=test-fb-id",
    "spring.security.oauth2.client.registration.facebook.client-secret=test-fb-secret",
    "spring.security.oauth2.client.registration.x.client-id=test-x-id",
    "spring.security.oauth2.client.registration.x.client-secret=test-x-secret"
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
    void chuyenHuongSangFacebookKemDungThamSo() throws Exception {
        String redirect = authorizeRedirect("facebook");

        assertThat(redirect).startsWith("https://www.facebook.com/");
        assertThat(redirect).contains("client_id=test-fb-id");
        assertThat(redirect).contains("response_type=code");
        assertThat(redirect).contains("state=");
    }

    @Test
    void chuyenHuongSangXKemDungThamSo() throws Exception {
        String redirect = authorizeRedirect("x");

        assertThat(redirect).startsWith("https://x.com/i/oauth2/authorize");
        assertThat(redirect).contains("client_id=test-x-id");
        assertThat(redirect).contains("scope=users.read");
    }

    // PKCE: X BẮT BUỘC phải có, mà Spring Security chỉ tự bật cho client không có secret.
    // Đây là phép kiểm cho dòng withPkce() trong SecurityConfig — thiếu nó thì X trả lỗi
    // ngay ở bước đầu tiên và rất khó lần ra nguyên nhân.
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
    //
    // Đây là hành vi sẵn có của OAuth2AuthorizationRequestRedirectFilter: nó ném
    // InvalidClientRegistrationIdException và kết quả là 500. Lỗi này phát sinh trong filter,
    // nằm ngoài DispatcherServlet nên GlobalExceptionHandler không bắt được.
    // Ghi lại ở đây để biết đúng hành vi hiện tại, tránh hiểu nhầm là đã xử lý tử tế.
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
