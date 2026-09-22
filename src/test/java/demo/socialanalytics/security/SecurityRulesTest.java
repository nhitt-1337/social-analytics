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
//
// Profile test không khai báo client id/secret nên không có Social Login — đúng ý đồ ở đây:
// phần này kiểm tra bảo mật nền, phần OAuth2 để riêng ở OAuth2LoginTest.
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

        // Client gọi API mong nhận JSON: trả 401 để phía gọi xử lý được,
        // không trả về trang HTML đăng nhập.
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

        // Đã đăng nhập nhưng KHÔNG kèm token -> bị chặn. Đây chính là điều CSRF bảo vệ:
        // trang web khác không lấy được token nên không thay mặt người dùng gửi lệnh được.
        @Test
        void thieuTokenThiBiChan403() throws Exception {
            mvc.perform(api("post", "/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"userId":1,"platform":"facebook","externalId":"fb-001"}
                        """))
                .andExpect(status().isForbidden());
        }

        // Có token thì đi tiếp vào controller. 404 là vì userId=1 không tồn tại —
        // điều cần chứng minh là KHÔNG còn 403.
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

        // Đăng xuất phải là POST kèm token: để GET thì chỉ cần dụ bấm một đường link
        // là đăng xuất được người khác.
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
