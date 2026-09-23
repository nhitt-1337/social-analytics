package demo.socialanalytics.messaging;

import demo.socialanalytics.config.JmsConfig;
import demo.socialanalytics.dto.response.PlatformSummaryResponse;
import demo.socialanalytics.service.ChartDataService;
import demo.socialanalytics.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Nhận IMPORT_COMPLETED rồi tính lại thống kê tổng hợp.
@Component
public class ImportCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ImportCompletedListener.class);

    private final StatisticsService statisticsService;
    private final ChartDataService chartDataService;
    private final DashboardBroadcaster broadcaster;

    public ImportCompletedListener(
        StatisticsService statisticsService,
        ChartDataService chartDataService,
        DashboardBroadcaster broadcaster
    ) {
        this.statisticsService = statisticsService;
        this.chartDataService = chartDataService;
        this.broadcaster = broadcaster;
    }

    @JmsListener(destination = Queues.IMPORT_COMPLETED, containerFactory = JmsConfig.LISTENER_FACTORY)
    public void onImportCompleted(ImportCompletedMessage message) {
        log.info("Nhận IMPORT_COMPLETED trên luồng {}: {} bài mới",
            Thread.currentThread().getName(), message.imported());

        // KHÔNG bắt exception ở đây. Ném ra là đúng ý: phiên có transaction sẽ rollback,
        var summaries = statisticsService.refresh();

        notifyDashboards(summaries);
    }

    // Phát tin SAU khi thống kê đã tính xong, và KHÔNG được để lỗi ở đây thoát ra.
    private void notifyDashboards(java.util.List<demo.socialanalytics.entity.PlatformSummary> summaries) {
        try {
            broadcaster.statisticsUpdated(
                summaries.stream().map(PlatformSummaryResponse::of).toList());
            broadcaster.chartUpdated(chartDataService.chartData(null, null));
        } catch (Exception exception) {
            log.warn("Không gửi được cập nhật realtime sau khi import: {}", exception.getMessage());
        }
    }
}
