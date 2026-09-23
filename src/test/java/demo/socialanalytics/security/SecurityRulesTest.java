package demo.socialanalytics.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Kiểm tra bộ quy tắc trong SecurityConfig: ai vào được đâu, và CSRF chặn cái gì.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

    @Autowired MockMvc mvc;

    private MockHttpServletRequestBuilder api(String method, String path) {
        var builder = switch (method) {
            case "post" -> post("/api/v1" + path);
            case "delete" -> delete("/api/v1" + path);
            default -> get("/api/v1" + path);
        };
        return builder.contextPath("/api/v1").servletPath(path);
    }

    private MockHttpServletRequestBuilder page(String method, String path) {
        return api(method, path).accept(MediaType.TEXT_HTML);
    }

    @Nested
    @DisplayName("Chưa đăng nhập")
    class Anonymous {

        // Client gọi API mong nhận JSON: trả 401 để phía gọi xử lý được, không trả về trang HTML đăng
        @Test
        void goiApiThiNhan401() throws Exception {
            mvc.perform(api("get", "/posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        // Trình duyệt mở trang thì chuyển hướng tới trang đăng nhập.
        @Test
        void moTrangHtmlThiChuyenHuongToiLogin() throws Exception {
            mvc.perform(page("get", "/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/login"));
        }

        // Trình duyệt thật gửi header Accept dài, KẾT THÚC bằng "*/*;q=0.8". Mà */* thì
        @Test
        void trinhDuyetThatDuocChuyenToiLogin() throws Exception {
            mvc.perform(get("/api/v1/dashboard").contextPath("/api/v1").servletPath("/dashboard")
                    .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,"
                            + "image/avif,image/webp,image/apng,*/*;q=0.8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/login"));
        }

        @Test
        void trangDangNhapLaCongKhai() throws Exception {
            mvc.perform(page("get", "/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
        }

        @Test
        void taiLieuApiLaCongKhai() throws Exception {
            mvc.perform(api("get", "/v3/api-docs"))
                .andExpect(status().isOk());
        }

        // Endpoint WebSocket được miễn CSRF (SockJS không gắn được token khi lùi về HTTP), nên nó phải
        @Test
        void khongDangNhapThiKhongMoDuocWebSocket() throws Exception {
            mvc.perform(page("get", "/ws/info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/login"));
        }

        @Test
        void khongDangNhapThiKhongXemDuocDuLieuBieuDo() throws Exception {
            mvc.perform(api("get", "/chart-data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void khongDangNhapThiKhongXuatDuocBaoCao() throws Exception {
            mvc.perform(api("get", "/export-report").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("CSRF")
    @WithMockUser
    class Csrf {

        // Đã đăng nhập nhưng KHÔNG kèm token -> bị chặn
        @Test
        void thieuTokenThiBiChan403() throws Exception {
            mvc.perform(api("post", "/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"userId":1,"platform":"facebook","externalId":"fb-001"}
                        """))
                .andExpect(status().isForbidden());
        }

        // Có token thì đi tiếp vào controller
        @Test
        void coTokenThiDiTiepVaoController() throws Exception {
            mvc.perform(api("post", "/posts").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"userId":1,"platform":"facebook","externalId":"fb-001"}
                        """))
                .andExpect(status().isNotFound());
        }

        @Test
        void tokenSaiCungBiChan403() throws Exception {
            mvc.perform(api("post", "/posts").with(csrf().useInvalidToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        void deleteThieuTokenCungBiChan() throws Exception {
            mvc.perform(api("delete", "/posts/1"))
                .andExpect(status().isForbidden());
        }

        // /ws được miễn CSRF có chủ đích: SockJS khi lùi về HTTP dùng POST mà không gắn được token, có
        @Test
        void endpointWebSocketDuocMienCsrf() throws Exception {
            mvc.perform(api("post", "/ws/info"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }

        // GET không làm thay đổi dữ liệu nên không cần token.
        @Test
        void getKhongCanToken() throws Exception {
            mvc.perform(api("get", "/posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

    }

    @Nested
    @DisplayName("Đã đăng nhập")
    @WithMockUser
    class Authenticated {

        @Test
        void vaoDuocDashboard() throws Exception {
            mvc.perform(page("get", "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
        }

        @Test
        void trangChuChuyenHuongVeDashboard() throws Exception {
            mvc.perform(get("/api/v1/").contextPath("/api/v1").servletPath("").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/dashboard"));
        }

        @Test
        void formCoTokenThiGuiDuoc() throws Exception {
            mvc.perform(api("post", "/dashboard/note").with(csrf()).param("note", "ghi chú"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/dashboard"));
        }

        @Test
        void formThieuTokenThiBiChan() throws Exception {
            mvc.perform(api("post", "/dashboard/note").param("note", "ghi chú"))
                .andExpect(status().isForbidden());
        }

        // Đăng xuất phải là POST kèm token: để GET thì chỉ cần dụ bấm một đường link là đăng xuất được
        @Test
        void dangXuatBangPostKemToken() throws Exception {
            mvc.perform(api("post", "/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/login?logout"));
        }

        @Test
        void dangXuatBangGetThiKhongDuoc() throws Exception {
            mvc.perform(page("get", "/logout"))
                .andExpect(status().isNotFound());
        }
    }
}
