package demo.socialanalytics.soap;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;

import java.nio.charset.StandardCharsets;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.*;

// Phía TẠO endpoint SOAP.
//
// MockWebServiceClient gửi thẳng payload vào MessageDispatcher, bỏ qua HTTP — đúng phần cần
// kiểm: định tuyến theo @PayloadRoot và việc dịch XML sang đối tượng rồi ngược lại.
@SpringBootTest
@ActiveProfiles("test")
class SocialAnalyticsEndpointTest {

    private static final String NS = "http://demo/socialanalytics/ws";

    @Autowired ApplicationContext applicationContext;
    @Autowired StatisticsService statisticsService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired PlatformSummaryRepository summaries;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(applicationContext);
        metrics.deleteAll();
        posts.deleteAll();
        summaries.deleteAll();
        users.deleteAll();
    }

    private ByteArrayResource xml(String body) {
        return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8));
    }

    private void seedPosts() {
        User owner = new User();
        owner.setEmail("a@example.com");
        owner.setFullName("Chủ tài khoản");
        owner.setRole(Role.ADMIN);
        owner = users.save(owner);

        for (String id : new String[]{"fb-1", "fb-2"}) {
            Post post = new Post();
            post.setUser(owner);
            post.setPlatform(Platform.FACEBOOK);
            post.setExternalId(id);
            posts.save(post);
        }
        statisticsService.refresh();
    }

    @Test
    void traVeThongKeTheoNenTang() throws Exception {
        seedPosts();

        client.sendRequest(withPayload(xml("""
                <getPlatformSummaryRequest xmlns="%s"/>
                """.formatted(NS))))
            .andExpect(noFault())
            .andExpect(xpath("//ns:getPlatformSummaryResponse/ns:summary[ns:platform='facebook']/ns:postCount",
                java.util.Map.of("ns", NS)).evaluatesTo(2));
    }

    @Test
    void locTheoNenTang() throws Exception {
        seedPosts();

        client.sendRequest(withPayload(xml("""
                <getPlatformSummaryRequest xmlns="%s">
                    <platform>facebook</platform>
                </getPlatformSummaryRequest>
                """.formatted(NS))))
            .andExpect(noFault())
            .andExpect(xpath("count(//ns:getPlatformSummaryResponse/ns:summary)",
                java.util.Map.of("ns", NS)).evaluatesTo(1));
    }

    @Test
    void traVeTyGia() throws Exception {
        client.sendRequest(withPayload(xml("""
                <getExchangeRateRequest xmlns="%s">
                    <fromCurrency>USD</fromCurrency>
                    <toCurrency>VND</toCurrency>
                </getExchangeRateRequest>
                """.formatted(NS))))
            .andExpect(noFault())
            .andExpect(xpath("//ns:getExchangeRateResponse/ns:rate", java.util.Map.of("ns", NS))
                .evaluatesTo(25400));
    }

    // Tiền tệ không hỗ trợ -> SOAP Fault, không phải trả về 0 hay để rỗng.
    @Test
    void tienTeKhongHoTroThiTraVeSoapFault() throws Exception {
        client.sendRequest(withPayload(xml("""
                <getExchangeRateRequest xmlns="%s">
                    <fromCurrency>USD</fromCurrency>
                    <toCurrency>XXX</toCurrency>
                </getExchangeRateRequest>
                """.formatted(NS))))
            .andExpect(clientOrSenderFault());
    }

    // Định tuyến SOAP dựa trên TÊN PHẦN TỬ GỐC kèm namespace, không dựa trên URL.
    // Sai namespace là không endpoint nào khớp — request không tới được chỗ xử lý.
    @Test
    void namespaceSaiThiKhongEndpointNaoKhop() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                client.sendRequest(withPayload(xml("""
                    <getExchangeRateRequest xmlns="http://sai/namespace">
                        <fromCurrency>USD</fromCurrency><toCurrency>VND</toCurrency>
                    </getExchangeRateRequest>
                    """))))
            .hasMessageContaining("No endpoint can be found");
    }
}
