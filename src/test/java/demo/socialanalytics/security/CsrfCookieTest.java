package demo.socialanalytics.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Tách riêng khỏi SecurityRulesTest vì phép kiểm này cần CsrfFilter còn NGUYÊN BẢN.
//
// SecurityMockMvcRequestPostProcessors.csrf() thay CsrfTokenRepository ngay trên instance
// CsrfFilter dùng chung của context; sau khi một test nào đó gọi .with(csrf()) thì các request
// tiếp theo không còn ghi cookie thật nữa. @DirtiesContext yêu cầu một context sạch cho class
// này nên kết quả không phụ thuộc thứ tự chạy.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@WithMockUser
class CsrfCookieTest {

    @Autowired MockMvc mvc;

    // Cookie XSRF-TOKEN phải có mặt ngay từ lần tải trang đầu tiên, nếu không JavaScript
    // không có gì để gắn vào header X-XSRF-TOKEN. Đây là việc của CsrfCookieFilter —
    // từ Spring Security 6, token nạp lười nên không chạm vào thì cookie không được gửi.
    @Test
    void traVeCookieXsrfTokenChoJavaScriptDoc() throws Exception {
        var result = mvc.perform(get("/api/v1/dashboard")
                .contextPath("/api/v1").servletPath("/dashboard")
                .accept(MediaType.TEXT_HTML))
            .andReturn();

        var cookie = result.getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        // Không HttpOnly thì JavaScript mới đọc được để gọi API bằng fetch.
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
