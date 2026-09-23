package demo.socialanalytics.messaging;

import demo.socialanalytics.entity.DeadLetter;
import demo.socialanalytics.repository.DeadLetterRepository;
import org.apache.activemq.command.ActiveMQMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Trực DLQ: message nào thử lại hết số lần vẫn hỏng thì ghi lại xuống DB.
@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    // Cột payload là text nhưng vẫn cắt bớt: message khổng lồ không có ích gì cho việc chẩn đoán.
    private static final int MAX_PAYLOAD = 10_000;
    private static final int MAX_CAUSE = 2_000;

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterListener(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    @JmsListener(destination = Queues.DEAD_LETTER)
    @Transactional
    public void onDeadLetter(Message message) {
        DeadLetter record = new DeadLetter();
        record.setSourceQueue(originalQueue(message));
        record.setMessageId(readMessageId(message));
        record.setPayload(readPayload(message));
        record.setFailureCause(readFailureCause(message));
        record.setReceivedAt(LocalDateTime.now());

        deadLetterRepository.save(record);
        log.error("Message vào DLQ từ hàng đợi {} — {} — nội dung: {}",
            record.getSourceQueue(), record.getFailureCause(), record.getPayload());
    }

    // Hàng đợi GỐC của message — thứ cần nhất khi chẩn đoán, vì DLQ dùng chung cho mọi hàng đợi.
    private String originalQueue(Message message) {
        if (message instanceof ActiveMQMessage activeMq && activeMq.getOriginalDestination() != null) {
            return activeMq.getOriginalDestination().getPhysicalName();
        }
        // Một số cấu hình broker có đặt property này; thử nốt trước khi bỏ cuộc.
        try {
            String original = message.getStringProperty("originalDestination");
            return original != null ? original : Queues.DEAD_LETTER;
        } catch (JMSException exception) {
            return Queues.DEAD_LETTER;
        }
    }

    private String readMessageId(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException exception) {
            return null;
        }
    }

    // JMSXDeliveryCount và getRedeliveryCounter() đều không mang giá trị gốc sang DLQ
    private String readFailureCause(Message message) {
        try {
            String cause = message.getStringProperty("dlqDeliveryFailureCause");
            if (cause == null) {
                return null;
            }
            return cause.length() <= MAX_CAUSE ? cause : cause.substring(0, MAX_CAUSE) + "...(cắt bớt)";
        } catch (JMSException exception) {
            return null;
        }
    }

    // Nuốt mọi lỗi đọc: ném ở đây là message quay lại DLQ và lặp vô hạn
    private String readPayload(Message message) {
        try {
            if (message instanceof TextMessage text) {
                String body = text.getText();
                if (body == null) {
                    return "(rỗng)";
                }
                return body.length() <= MAX_PAYLOAD ? body : body.substring(0, MAX_PAYLOAD) + "...(cắt bớt)";
            }
            return "(không phải TextMessage: " + message.getClass().getSimpleName() + ")";
        } catch (Exception exception) {
            return "(không đọc được nội dung: " + exception.getMessage() + ")";
        }
    }
}
