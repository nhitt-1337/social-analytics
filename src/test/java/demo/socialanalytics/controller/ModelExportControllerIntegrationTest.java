package demo.socialanalytics.controller;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.support.ExcelTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelExportControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();

        User owner = new User();
        owner.setEmail("a@example.com");
        owner.setFullName("Chủ tài khoản");
        owner.setRole(Role.ADMIN);
        owner = users.save(owner);

        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(Platform.FACEBOOK);
        post.setExternalId("fb-1");
        post.setContent("Nội dung");
        posts.save(post);
    }

    private MockHttpServletRequestBuilder api(String path) {
        return get("/api/v1" + path).contextPath("/api/v1").servletPath(path);
    }

    @Test
    void chuaDangNhapThiKhongXuatDuoc() throws Exception {
        mvc.perform(api("/export/posts").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    // Liệt kê được model nào xuất được và mỗi model có cột gì — cột do Reflection suy ra.
    @Test
    @WithMockUser
    void lietKeModelVaCotCuaTungModel() throws Exception {
        mvc.perform(api("/export/models").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(4))
            .andExpect(jsonPath("$.data[?(@.model == 'posts')].columns[0]").value("Id"))
            .andExpect(jsonPath("$.data[?(@.model == 'statistics')].columns").isNotEmpty());
    }

    @Test
    @WithMockUser
    void xuatDuocModelPosts() throws Exception {
        var result = mvc.perform(api("/export/posts"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn();

        var sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).contains("Id", "Platform", "External id");
        assertThat(sheet).hasSize(2);
        assertThat(sheet.get(1)).contains("facebook", "fb-1");
    }

    // Cùng một endpoint xuất được nhiều loại model khác nhau.
    @Test
    @WithMockUser
    void cungMotEndpointXuatDuocNhieuModel() throws Exception {
        for (String model : new String[]{"posts", "crawl-runs", "statistics", "dead-letters"}) {
            var result = mvc.perform(api("/export/" + model))
                .andExpect(status().isOk())
                .andReturn();
            // Model nào cũng ra file hợp lệ, ít nhất có dòng tiêu đề.
            assertThat(ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray()))
                .isNotEmpty();
        }
    }

    @Test
    @WithMockUser
    void modelKhongTonTaiThiTraVe400KemDanhSachChoPhep() throws Exception {
        mvc.perform(api("/export/khong-co-model-nay").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("posts")));
    }

    @Test
    @WithMockUser
    void tenModelKhongPhanBietHoaThuong() throws Exception {
        mvc.perform(api("/export/POSTS")).andExpect(status().isOk());
    }
}
