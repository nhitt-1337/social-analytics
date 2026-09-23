package demo.socialanalytics;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.service.*;
import demo.socialanalytics.soap.ExchangeRateClient;
import demo.socialanalytics.support.ExcelTestFiles;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.ws.test.client.MockWebServiceServer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@SpringBootTest(properties = {
    "social.crawl.enabled=true",
    "social.crawl.initial-delay=PT24H",
    "social.crawl.mock.latency-ms=0",
    "social.crawl.mock.failure-rate=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndIntegrationTest {
    private static final String NS = "http://demo/socialanalytics/ws";

    @Autowired MockMvc mvc;
    @Autowired SocialMetricsUpdateJob crawlJob;
    @Autowired StatisticsService statisticsService;
    @Autowired ExchangeRateClient exchangeRateClient;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired PlatformSummaryRepository summaries;
    @Autowired CrawlRunRepository crawlRuns;

    private Long adminId;

    @BeforeAll
    void cleanSlate() {
        metrics.deleteAll();
        posts.deleteAll();
        summaries.deleteAll();
        crawlRuns.deleteAll();
        users.deleteAll();

        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        adminId = users.save(admin).getId();
    }

    @Test
    @Order(1)
    void buoc1_importExcelLuuBaiViet() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "posts.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ExcelTestFiles.postsFile(List.of(
                List.of("facebook", "e2e-fb-1", "Bài FB 1", "https://fb.com/1",
                    LocalDateTime.now().minusDays(3)),
                List.of("facebook", "e2e-fb-2", "Bài FB 2", "", LocalDateTime.now().minusDays(2)),
                List.of("twitter", "e2e-tw-1", "Bài TW 1", "", LocalDateTime.now().minusDays(1)))));

        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/import-posts")
                .file(file)
                .contextPath("/api/v1").servletPath("/import-posts")
                .param("userId", String.valueOf(adminId))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(3));

        assertThat(posts.findAll()).hasSize(3);
    }

    @Test
    @Order(2)
    void buoc2_messageJmsCapNhatThongKe() {
        await().atMost(ofSeconds(15)).untilAsserted(() -> {
            assertThat(summaries.findByPlatform(Platform.FACEBOOK))
                .get().satisfies(s -> assertThat(s.getPostCount()).isEqualTo(2));
            assertThat(summaries.findByPlatform(Platform.TWITTER))
                .get().satisfies(s -> assertThat(s.getPostCount()).isEqualTo(1));
        });
    }

    @Test
    @Order(3)
    void buoc3_jobCrawlGhiChiSoChoTungBai() {
        CrawlRun run = crawlJob.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalPosts()).isEqualTo(3);
        assertThat(metrics.findAll()).hasSize(3);
    }

    @Test
    @Order(4)
    void buoc4_chartDataGopSoLieuTheoNgay() throws Exception {
        mvc.perform(get("/api/v1/chart-data").contextPath("/api/v1").servletPath("/chart-data")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.labels.length()").value(1))
            .andExpect(jsonPath("$.likes[0]").value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.platforms.length()").value(Platform.values().length));
    }

    @Test
    @Order(5)
    void buoc5_soapTraVeThongKeVuaTinh() throws Exception {
        org.springframework.ws.test.server.MockWebServiceClient soapClient =
            org.springframework.ws.test.server.MockWebServiceClient.createClient(
                applicationContext());

        soapClient.sendRequest(org.springframework.ws.test.server.RequestCreators.withPayload(
                new org.springframework.core.io.ByteArrayResource("""
                    <getPlatformSummaryRequest xmlns="%s">
                        <platform>facebook</platform>
                    </getPlatformSummaryRequest>
                    """.formatted(NS).getBytes(StandardCharsets.UTF_8))))
            .andExpect(org.springframework.ws.test.server.ResponseMatchers.noFault())
            .andExpect(org.springframework.ws.test.server.ResponseMatchers.xpath(
                    "//ns:getPlatformSummaryResponse/ns:summary/ns:postCount",
                    java.util.Map.of("ns", NS))
                .evaluatesTo(2));
    }

    @Autowired org.springframework.context.ApplicationContext context;

    private org.springframework.context.ApplicationContext applicationContext() {
        return context;
    }

    @Test
    @Order(6)
    void buoc6_goiWebServiceTyGiaBenNgoai() throws Exception {
        MockWebServiceServer server = MockWebServiceServer.createServer(
            exchangeRateClient.webServiceTemplate());
        server.expect(anything()).andRespond(withPayload(
            new org.springframework.core.io.ByteArrayResource("""
                <getExchangeRateResponse xmlns="%s">
                    <fromCurrency>USD</fromCurrency><toCurrency>VND</toCurrency>
                    <rate>25400</rate><quotedAt>2026-09-23T10:00:00</quotedAt>
                </getExchangeRateResponse>
                """.formatted(NS).getBytes(StandardCharsets.UTF_8))));

        mvc.perform(get("/api/v1/exchange-rate").contextPath("/api/v1").servletPath("/exchange-rate")
                .param("from", "USD").param("to", "VND")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rate").value(25400));

        server.verify();
    }

    @Test
    @Order(7)
    void buoc7_xuatModelRaExcelBangReflection() throws Exception {
        MvcResult result = mvc.perform(
                get("/api/v1/export/posts").contextPath("/api/v1").servletPath("/export/posts"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString(".xlsx")))
            .andReturn();

        var sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).containsExactly(
            "Id", "Platform", "External id", "Content", "Url", "Posted at", "Created at");
        assertThat(sheet).hasSize(4);
        assertThat(sheet.stream().skip(1).map(row -> row.get(2)).toList())
            .containsExactlyInAnyOrder("e2e-fb-1", "e2e-fb-2", "e2e-tw-1");
    }

    @Test
    @Order(8)
    void buoc8_xuatBaoCaoTuongTac() throws Exception {
        MvcResult result = mvc.perform(
                get("/api/v1/export-report").contextPath("/api/v1").servletPath("/export-report"))
            .andExpect(status().isOk())
            .andReturn();

        var sheet = ExcelTestFiles.readAll(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).first().isEqualTo("ID");
        assertThat(sheet).hasSize(4);
        assertThat(sheet.get(1).get(8)).isNotEmpty();
    }

    @Test
    @Order(9)
    void buoc9_dashboardVaTrangThaiPhanAnhDungMoiThu() throws Exception {
        mvc.perform(get("/api/v1/crawl/last-run").contextPath("/api/v1").servletPath("/crawl/last-run")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        mvc.perform(get("/api/v1/statistics").contextPath("/api/v1").servletPath("/statistics")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(Platform.values().length));

        mvc.perform(get("/api/v1/statistics/dead-letters")
                .contextPath("/api/v1").servletPath("/statistics/dead-letters")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/v1/dashboard").contextPath("/api/v1").servletPath("/dashboard")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cập nhật lần cuối")));
    }
}
