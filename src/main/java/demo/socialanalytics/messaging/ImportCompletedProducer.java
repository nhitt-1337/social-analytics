package demo.socialanalytics.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Chỉ gửi sau khi transaction commit, tránh listener đọc dữ liệu chưa thấy
@Component
public class ImportCompletedProducer {
    private static final Logger log = LoggerFactory.getLogger(ImportCompletedProducer.class);

    private final JmsTemplate jmsTemplate;

    public ImportCompletedProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ImportCompletedEvent event) {
        ImportCompletedMessage message = event.message();
        jmsTemplate.convertAndSend(Queues.IMPORT_COMPLETED, message);
        log.info("Đã gửi {} lên hàng đợi {}: {} bài mới của người dùng {}",
            "IMPORT_COMPLETED", Queues.IMPORT_COMPLETED, message.imported(), message.userId());
    }
}
