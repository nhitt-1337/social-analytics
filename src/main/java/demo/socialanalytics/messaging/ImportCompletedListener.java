package demo.socialanalytics.messaging;

import demo.socialanalytics.config.JmsConfig;
import demo.socialanalytics.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Nhận IMPORT_COMPLETED rồi tính lại thống kê tổng hợp.
//
// Chạy trên luồng của listener container, tách hẳn khỏi request upload file: người dùng nhận
// được phản hồi ngay khi bài viết đã lưu xong, không phải đợi phần thống kê.
@Component
public class ImportCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ImportCompletedListener.class);

    private final StatisticsService statisticsService;

    public ImportCompletedListener(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @JmsListener(destination = Queues.IMPORT_COMPLETED, containerFactory = JmsConfig.LISTENER_FACTORY)
    public void onImportCompleted(ImportCompletedMessage message) {
        log.info("Nhận IMPORT_COMPLETED trên luồng {}: {} bài mới",
            Thread.currentThread().getName(), message.imported());

        // KHÔNG bắt exception ở đây. Ném ra là đúng ý: phiên có transaction sẽ rollback,
        // broker giao lại theo RedeliveryPolicy, hết số lần thì đẩy sang DLQ.
        // Bắt rồi nuốt đi thì mất sạch cơ chế thử lại.
        statisticsService.refresh();
    }
}
