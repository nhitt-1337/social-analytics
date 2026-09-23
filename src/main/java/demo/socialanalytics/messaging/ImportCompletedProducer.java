package demo.socialanalytics.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Đẩy message IMPORT_COMPLETED lên hàng đợi.
//
// Điểm mấu chốt: @TransactionalEventListener(AFTER_COMMIT) — chỉ gửi SAU KHI transaction import
// đã commit.
//
// Gửi thẳng JMS từ trong PostImportService.importPosts() (đang mở transaction) sẽ hỏng theo hai
// kiểu, cả hai đều khó lần ra vì chỉ thỉnh thoảng mới xảy ra:
//   1. Listener chạy trên luồng khác, nhận message trước khi dữ liệu được commit -> tính thống kê
//      thiếu đúng những bài vừa import.
//   2. Transaction rollback sau khi đã gửi -> message báo "import xong" cho một lần import
//      không hề tồn tại.
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
