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

// csrf() thay CsrfTokenRepository trên CsrfFilter dùng chung -> phải tách context riêng
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@WithMockUser
class CsrfCookieTest {
    @Autowired MockMvc mvc;

    @Test
    void traVeCookieXsrfToken() throws Exception {
        var result = mvc.perform(get("/api/v1/dashboard")
                .contextPath("/api/v1").servletPath("/dashboard")
                .accept(MediaType.TEXT_HTML))
            .andReturn();

        var cookie = result.getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
