package demo.socialanalytics.controller;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.Role;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.repository.UserRepository;
import demo.socialanalytics.support.ExcelTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Test đầu-cuối cho hai API Excel: đi qua HTTP thật, POI thật, DB thật (H2).
// Đây là chỗ duy nhất chứng minh được cả chuỗi upload -> đọc file -> lưu DB -> xuất file chạy đúng.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Hai endpoint Excel đều yêu cầu đăng nhập; test nhắm vào hành vi nhập/xuất nên giả lập
// sẵn người dùng thay vì đi qua luồng OAuth2 thật.
@WithMockUser
class ExcelControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;

    private User admin;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        admin = users.save(admin);
    }

    // ----- helper -----

    private MockMultipartHttpServletRequestBuilder importRequest(byte[] content, String fileName) {
        MockMultipartFile file = new MockMultipartFile("file", fileName,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        return MockMvcRequestBuilders.multipart("/api/v1/import-posts")
            .file(file)
            .contextPath("/api/v1").servletPath("/import-posts")
            .param("userId", String.valueOf(admin.getId()))
            .with(csrf());
    }

    private MockHttpServletRequestBuilder exportRequest() {
        return MockMvcRequestBuilders.get("/api/v1/export-report")
            .contextPath("/api/v1").servletPath("/export-report");
    }

    private Post savePost(Platform platform, String externalId, LocalDateTime postedAt) {
        Post post = new Post();
        post.setUser(admin);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        post.setContent("Nội dung " + externalId);
        post.setPostedAt(postedAt);
        return posts.save(post);
    }

    // ----- POST /import-posts -----

    @Test
    void importLuuBaiVietVaoDatabase() throws Exception {
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Bài 1", "https://fb.com/1", LocalDateTime.of(2026, 3, 1, 9, 0)),
            List.of("twitter", "tw-002", "Bài 2", "https://x.com/2", LocalDateTime.of(2026, 3, 2, 9, 0))));

        mvc.perform(importRequest(file, "posts.xlsx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.imported").value(2))
            .andExpect(jsonPath("$.skipped").value(0))
            .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(posts.findAll()).hasSize(2);
        assertThat(posts.findByPlatformAndExternalId(Platform.FACEBOOK, "fb-001"))
            .get()
            .satisfies(post -> {
                assertThat(post.getContent()).isEqualTo("Bài 1");
                assertThat(post.getUrl()).isEqualTo("https://fb.com/1");
                assertThat(post.getPostedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 9, 0));
                assertThat(post.getUser().getId()).isEqualTo(admin.getId());
            });
    }

    // Import lại chính file cũ không được tạo bản ghi trùng.
    @Test
    void importLaiCungFileThiBoQuaToanBo() throws Exception {
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Bài 1", "", ""),
            List.of("twitter", "tw-002", "Bài 2", "", "")));

        mvc.perform(importRequest(file, "posts.xlsx")).andExpect(status().isOk());

        mvc.perform(importRequest(file, "posts.xlsx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.skipped").value(2))
            .andExpect(jsonPath("$.errors.length()").value(2));

        assertThat(posts.findAll()).hasSize(2);
    }

    // Dòng hỏng bị loại, dòng tốt vẫn vào DB — không "được ăn cả ngã về không".
    @Test
    void dongLoiBiBoQuaNhungDongHopLeVanDuocLuu() throws Exception {
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Hợp lệ", "", ""),
            List.of("instagram", "ig-001", "Nền tảng lạ", "", ""),
            Arrays.asList(null, "fb-003", "Thiếu nền tảng", "", "")));

        mvc.perform(importRequest(file, "posts.xlsx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(3))
            .andExpect(jsonPath("$.imported").value(1))
            .andExpect(jsonPath("$.skipped").value(2))
            .andExpect(jsonPath("$.errors[0].rowNumber").value(3))
            .andExpect(jsonPath("$.errors[1].rowNumber").value(4));

        assertThat(posts.findAll()).extracting(Post::getExternalId).containsExactly("fb-001");
    }

    @Test
    void thieuCotBatBuocThiTraVe400() throws Exception {
        byte[] file = ExcelTestFiles.file(List.of("content", "url"),
            List.of(List.of("Thiếu platform", "https://example.com")));

        mvc.perform(importRequest(file, "posts.xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("platform")));

        assertThat(posts.findAll()).isEmpty();
    }

    @Test
    void fileKhongPhaiExcelThiTraVe400() throws Exception {
        byte[] notExcel = "chỉ là văn bản thường".getBytes(StandardCharsets.UTF_8);

        mvc.perform(importRequest(notExcel, "posts.xlsx"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("không phải file Excel hợp lệ")));
    }

    @Test
    void duoiFileKhongPhaiExcelThiTraVe400() throws Exception {
        mvc.perform(importRequest("platform,externalId".getBytes(StandardCharsets.UTF_8), "posts.csv"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString(".xlsx")));
    }

    @Test
    void nguoiDungKhongTonTaiThiTraVe404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "posts.xlsx", null,
            ExcelTestFiles.postsFile(List.of(List.of("facebook", "fb-001", "", "", ""))));

        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/import-posts")
                .file(file)
                .contextPath("/api/v1").servletPath("/import-posts")
                .param("userId", "999999")
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void thieuPhanFileThiTraVe400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/import-posts")
                .contextPath("/api/v1").servletPath("/import-posts")
                .param("userId", String.valueOf(admin.getId()))
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ----- GET /export-report -----

    @Test
    void exportTraVeFileExcelDungKieuNoiDungVaTenFile() throws Exception {
        savePost(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 3, 1, 9, 0));

        MvcResult result = mvc.perform(exportRequest())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                org.hamcrest.Matchers.containsString(".xlsx")))
            .andReturn();

        List<List<String>> sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).first().isEqualTo("ID");
        assertThat(sheet.get(1)).contains("facebook", "fb-001", "admin@example.com");
    }

    @Test
    void exportKemSoLieuCuaLanDoGanNhat() throws Exception {
        Post post = savePost(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 3, 1, 9, 0));
        saveMetric(post, 100, LocalDateTime.of(2026, 4, 1, 8, 0));
        saveMetric(post, 750, LocalDateTime.of(2026, 4, 5, 8, 0));

        MvcResult result = mvc.perform(exportRequest()).andExpect(status().isOk()).andReturn();

        List<List<String>> sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        // Cột 9 (chỉ số 8) là "Lượt thích".
        assertThat(sheet.get(1).get(8)).isEqualTo("750");
    }

    @Test
    void exportLocTheoNenTang() throws Exception {
        savePost(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 3, 1, 9, 0));
        savePost(Platform.TWITTER, "tw-001", LocalDateTime.of(2026, 3, 2, 9, 0));

        MvcResult result = mvc.perform(exportRequest().param("platform", "twitter"))
            .andExpect(status().isOk()).andReturn();

        List<List<String>> sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet).hasSize(2);
        assertThat(sheet.get(1)).contains("twitter", "tw-001");
    }

    @Test
    void exportLocTheoKhoangThoiGian() throws Exception {
        savePost(Platform.FACEBOOK, "thang-1", LocalDateTime.of(2026, 1, 15, 9, 0));
        savePost(Platform.FACEBOOK, "thang-3", LocalDateTime.of(2026, 3, 15, 9, 0));

        MvcResult result = mvc.perform(exportRequest()
                .param("from", "2026-03-01T00:00:00")
                .param("to", "2026-03-31T23:59:59"))
            .andExpect(status().isOk()).andReturn();

        List<List<String>> sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet).hasSize(2);
        assertThat(sheet.get(1)).contains("thang-3");
    }

    @Test
    void exportNenTangKhongHopLeThiTraVe400() throws Exception {
        mvc.perform(exportRequest().param("platform", "instagram"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void exportFromSauToThiTraVe400() throws Exception {
        mvc.perform(exportRequest()
                .param("from", "2026-05-01T00:00:00")
                .param("to", "2026-04-01T00:00:00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("from phải trước to")));
    }

    @Test
    void exportKhongCoBaiNaoThiVanTraFileChiCoTieuDe() throws Exception {
        MvcResult result = mvc.perform(exportRequest()).andExpect(status().isOk()).andReturn();

        assertThat(ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray())).hasSize(1);
    }

    // ----- vòng tròn import -> export -----

    @Test
    void importXongExportRaDungNhungBaiVuaNhap() throws Exception {
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Bài 1", "https://fb.com/1", LocalDateTime.of(2026, 3, 1, 9, 0)),
            List.of("twitter", "tw-002", "Bài 2", "https://x.com/2", LocalDateTime.of(2026, 3, 2, 9, 0))));

        mvc.perform(importRequest(file, "posts.xlsx")).andExpect(status().isOk());

        MvcResult result = mvc.perform(exportRequest()).andExpect(status().isOk()).andReturn();
        List<List<String>> sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());

        assertThat(sheet).hasSize(3);
        assertThat(sheet.stream().skip(1).map(row -> row.get(2)).toList())
            .containsExactlyInAnyOrder("fb-001", "tw-002");
    }

    private void saveMetric(Post post, int likes, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        metric.setPost(post);
        metric.setLikes(likes);
        metric.setShares(likes / 2);
        metric.setComments(likes / 4);
        metric.setFollowers(5_000);
        metric.setCollectedAt(collectedAt);
        metrics.save(metric);
    }
}
