package demo.socialanalytics.messaging;

import demo.socialanalytics.config.WebSocketConfig;
import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.dto.response.CrawlRunResponse;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.dto.response.PlatformSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// Đẩy dữ liệu mới xuống các dashboard đang mở.
@Component
public class DashboardBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DashboardBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public DashboardBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Có chỉ số mới sau một lượt crawl -> gửi luôn dữ liệu biểu đồ đã tính sẵn.
    public void chartUpdated(ChartDataResponse chartData) {
        send(WebSocketConfig.TOPIC_CHART, chartData);
    }

    public void crawlFinished(CrawlRunResponse run) {
        send(WebSocketConfig.TOPIC_CRAWL, run);
    }

    public void statisticsUpdated(List<PlatformSummaryResponse> summaries) {
        send(WebSocketConfig.TOPIC_STATISTICS, ListResponse.of(summaries));
    }

    private void send(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(topic, payload);
            log.debug("Đã phát lên {}", topic);
        } catch (Exception exception) {
            log.warn("Không phát được cập nhật lên {}: {}", topic, exception.getMessage());
        }
    }
}
