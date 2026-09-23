package demo.socialanalytics.messaging;

import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.dto.response.CrawlRunResponse;
import demo.socialanalytics.config.WebSocketConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(WebSocketBroadcastTest.OpenWebSocketForTest.class)
class WebSocketBroadcastTest {
    @TestConfiguration
    static class OpenWebSocketForTest {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain openWebSocket(HttpSecurity http) throws Exception {
            http.securityMatcher(WebSocketConfig.ENDPOINT + "/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
            return http.build();
        }
    }

    @LocalServerPort int port;
    @Autowired DashboardBroadcaster broadcaster;

    private StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        String url = "ws://localhost:" + port + "/api/v1" + WebSocketConfig.ENDPOINT + "/websocket";
        return client.connectAsync(url, new StompSessionHandlerAdapter() {
        }).get(10, TimeUnit.SECONDS);
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, String topic, Class<T> type) {
        BlockingQueue<T> received = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.add(type.cast(payload));
            }
        });
        return received;
    }

    private ChartDataResponse sampleChartData() {
        return new ChartDataResponse(
            List.of(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23)),
            List.of(100L, 200L), List.of(10L, 20L), List.of(5L, 8L), List.of(900L, 950L),
            List.of(), LocalDateTime.now());
    }

    @Test
    void nhanDuocDuLieuBieuDo() throws Exception {
        StompSession session = connect();
        BlockingQueue<ChartDataResponse> received =
            subscribe(session, WebSocketConfig.TOPIC_CHART, ChartDataResponse.class);
        Thread.sleep(300);

        broadcaster.chartUpdated(sampleChartData());

        ChartDataResponse payload = received.poll(10, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.labels()).containsExactly(
            LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));
        assertThat(payload.likes()).containsExactly(100L, 200L);
        session.disconnect();
    }

    @Test
    void nhanDuocKetQuaCrawl() throws Exception {
        StompSession session = connect();
        BlockingQueue<CrawlRunResponse> received =
            subscribe(session, WebSocketConfig.TOPIC_CRAWL, CrawlRunResponse.class);
        Thread.sleep(300);

        broadcaster.crawlFinished(new CrawlRunResponse(
            1L, "SUCCESS", LocalDateTime.now(), LocalDateTime.now(), 1200, 2, 10, 10, 0, null));

        CrawlRunResponse payload = received.poll(10, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.status()).isEqualTo("SUCCESS");
        assertThat(payload.succeededPosts()).isEqualTo(10);
        session.disconnect();
    }

    @Test
    void chiNhanDuocChuDeDaDangKy() throws Exception {
        StompSession session = connect();
        BlockingQueue<CrawlRunResponse> crawlMessages =
            subscribe(session, WebSocketConfig.TOPIC_CRAWL, CrawlRunResponse.class);
        Thread.sleep(300);

        broadcaster.chartUpdated(sampleChartData());

        assertThat(crawlMessages.poll(2, TimeUnit.SECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void khongAiDangKyVanKhongLoi() {
        broadcaster.chartUpdated(sampleChartData());
        broadcaster.statisticsUpdated(List.of());
    }
}
