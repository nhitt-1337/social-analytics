package demo.socialanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// STOMP trên WebSocket để đẩy dữ liệu mới xuống dashboard.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Đường dẫn để trình duyệt mở kết nối.
    public static final String ENDPOINT = "/ws";

    // Các chủ đề server phát ra.
    public static final String TOPIC_CHART = "/topic/chart";
    public static final String TOPIC_CRAWL = "/topic/crawl";
    public static final String TOPIC_STATISTICS = "/topic/statistics";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker đơn giản chạy ngay trong ứng dụng, giữ danh sách người đăng ký trong bộ nhớ.
        registry.enableSimpleBroker("/topic");

        // Tiền tố cho message trình duyệt GỬI LÊN, tới các @MessageMapping.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
            // Chỉ nhận kết nối từ cùng nguồn gốc với trang web.
            .setAllowedOriginPatterns("http://localhost:[*]", "https://localhost:[*]")
            // SockJS: trình duyệt hoặc proxy nào chặn WebSocket thì tự lùi về HTTP long-polling.
            .withSockJS();
    }
}
