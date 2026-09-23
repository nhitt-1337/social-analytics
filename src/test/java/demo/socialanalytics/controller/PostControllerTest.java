package demo.socialanalytics.controller;

import demo.socialanalytics.dto.request.PostRequest;
import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.dto.response.PostResponse;
import demo.socialanalytics.dto.response.UserSummaryResponse;
import demo.socialanalytics.exception.DuplicateResourceException;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import demo.socialanalytics.config.SecurityConfig;
import demo.socialanalytics.security.SocialLoginUserService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Không @Import(SecurityConfig) thì @WebMvcTest dùng chain mặc định, không phải của app
@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
@WithMockUser
class PostControllerTest {

    private static final String BASE = "/api/v1/posts";

    @Autowired MockMvc mvc;

    @MockitoBean PostService postService;

    // SecurityConfig cần bean này để dựng oauth2Login; lát cắt web không nạp @Service nên phải
    @MockitoBean SocialLoginUserService socialLoginUserService;

    private PostResponse sampleResponse() {
        return new PostResponse(
            1L, "facebook", "fb-001", "Nội dung", "https://example.com/p",
            LocalDateTime.of(2026, 3, 1, 9, 0),
            new UserSummaryResponse(1L, "Quản trị viên", "admin@example.com"),
            new MetricResponse(9L, 1L, 500, 40, 12, 5_000, LocalDateTime.of(2026, 4, 1, 8, 0)),
            LocalDateTime.of(2026, 3, 1, 10, 0));
    }

    private String validBody() {
        return """
            {"userId":1,"platform":"facebook","externalId":"fb-001","content":"Nội dung"}
            """;
    }

    // ----- GET /posts -----

    @Test
    void danhSachTraVeKhuonPhanTrang() throws Exception {
        when(postService.list(isNull(), eq(1), eq(20)))
            .thenReturn(new PageResponse<>(List.of(sampleResponse()), 1, 1, 20));

        mvc.perform(req("get", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.limit").value(20))
            .andExpect(jsonPath("$.data[0].externalId").value("fb-001"))
            .andExpect(jsonPath("$.data[0].latestMetric.likes").value(500));
    }

    // Không truyền page/limit thì controller phải tự điền mặc định 1 và 20.
    @Test
    void dungGiaTriMacDinhKhiKhongTruyenPageVaLimit() throws Exception {
        when(postService.list(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 1, 20));

        mvc.perform(req("get", ""))
            .andExpect(status().isOk());

        verify(postService).list(null, 1, 20);
    }

    @Test
    void chuyenTiepThamSoLocXuongService() throws Exception {
        when(postService.list(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 2, 5));

        mvc.perform(req("get", "")
                .param("platform", "twitter").param("page", "2").param("limit", "5"))
            .andExpect(status().isOk());

        verify(postService).list("twitter", 2, 5);
    }

    @Test
    void pageNhoHon1ThiTraVe422() throws Exception {
        mvc.perform(req("get", "").param("page", "0"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION"))
            .andExpect(jsonPath("$.error.fields.page").exists());

        verifyNoInteractions(postService);
    }

    @Test
    void limitVuotTranThiTraVe422() throws Exception {
        mvc.perform(req("get", "").param("limit", "101"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields.limit").exists());

        verifyNoInteractions(postService);
    }

    // Lỗi do service ném ra phải được GlobalExceptionHandler đổi thành 400 đúng khuôn.
    @Test
    void nenTangKhongHopLeTraVe400() throws Exception {
        when(postService.list(eq("instagram"), anyInt(), anyInt()))
            .thenThrow(new InvalidRequestParameterException("Nền tảng không hợp lệ: instagram"));

        mvc.perform(req("get", "").param("platform", "instagram"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value(containsString("instagram")));
    }

    // ----- GET /posts/{id} -----

    @Test
    void chiTietTraVe200() throws Exception {
        when(postService.getById(1L)).thenReturn(sampleResponse());

        mvc.perform(req("get", "/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.user.email").value("admin@example.com"));
    }

    @Test
    void khongTimThayThiTraVe404() throws Exception {
        when(postService.getById(404L)).thenThrow(new ResourceNotFoundException("bài viết"));

        mvc.perform(req("get", "/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // id không phải số -> 400 với thông báo tiếng Việt, không lộ tên lớp Java.
    @Test
    void idKhongPhaiSoThiTraVe400() throws Exception {
        mvc.perform(req("get", "/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value(containsString("phải là số nguyên")));

        verifyNoInteractions(postService);
    }

    // ----- POST /posts -----

    @Test
    void taoMoiTraVe201() throws Exception {
        when(postService.create(any(PostRequest.class))).thenReturn(sampleResponse());

        mvc.perform(req("post", "")
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.externalId").value("fb-001"));
    }

    @Test
    void thieuTruongBatBuocThiTraVe422KemDanhSachLoi() throws Exception {
        mvc.perform(req("post", "")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION"))
            .andExpect(jsonPath("$.error.fields.userId").exists())
            .andExpect(jsonPath("$.error.fields.platform").exists())
            .andExpect(jsonPath("$.error.fields.externalId").exists());

        verifyNoInteractions(postService);
    }

    @Test
    void bodyKhongPhaiJsonHopLeThiTraVe400() throws Exception {
        mvc.perform(req("post", "")
                .contentType(MediaType.APPLICATION_JSON).content("{khong phai json"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    @Test
    void truongSaiKieuThiTraVe422() throws Exception {
        mvc.perform(req("post", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId":"khong-phai-so","platform":"facebook","externalId":"fb-001"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields.userId").exists());
    }

    @Test
    void taoTrungThiTraVe409() throws Exception {
        when(postService.create(any(PostRequest.class)))
            .thenThrow(new DuplicateResourceException("Bài viết fb-001 trên facebook đã tồn tại"));

        mvc.perform(req("post", "")
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // ----- PUT / DELETE -----

    @Test
    void capNhatTraVe200() throws Exception {
        when(postService.update(eq(1L), any(PostRequest.class))).thenReturn(sampleResponse());

        mvc.perform(req("put", "/1")
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isOk());

        verify(postService).update(eq(1L), any(PostRequest.class));
    }

    @Test
    void xoaTraVe204VaKhongCoBody() throws Exception {
        mvc.perform(req("delete", "/1"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(postService).delete(1L);
    }

    @Test
    void xoaBaiKhongTonTaiThiTraVe404() throws Exception {
        doThrow(new ResourceNotFoundException("bài viết")).when(postService).delete(404L);

        mvc.perform(req("delete", "/404"))
            .andExpect(status().isNotFound());
    }

    // MockMvc không tự suy ra context-path, phải khai báo tay; servletPath là phần ĐẦY ĐỦ sau
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req(
        String method, String suffix) {
        String url = BASE + suffix;
        String servletPath = "/posts" + suffix;
        var builder = switch (method) {
            case "post" -> post(url);
            case "put" -> put(url);
            case "delete" -> delete(url);
            default -> get(url);
        };
        builder.contextPath("/api/v1").servletPath(servletPath);
        // CSRF bật cho toàn ứng dụng nên mọi request làm thay đổi dữ liệu đều phải kèm token
        if (!"get".equals(method)) {
            builder.with(csrf());
        }
        return builder;
    }

    private static org.hamcrest.Matcher<String> containsString(String text) {
        return org.hamcrest.Matchers.containsString(text);
    }

    private static Integer anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
