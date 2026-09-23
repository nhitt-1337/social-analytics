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
//
// Nhận Message thô chứ không phải ImportCompletedMessage: message vào DLQ có thể hỏng ngay ở
// khâu chuyển đổi (JSON sai định dạng, thiếu property _type). Khai kiểu cụ thể thì chính
// listener này cũng hỏng theo, và message rơi vào vòng lặp không lối thoát.
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
    //
    // ActiveMQ không đặt nó thành property JMS thường; nó nằm trong trường riêng của
    // ActiveMQMessage. Vì vậy phải ép kiểu về lớp của ActiveMQ — chỗ duy nhất trong ứng dụng
    // phụ thuộc vào thư viện broker cụ thể. Đổi sang broker khác thì sửa đúng method này.
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

    // Lý do message bị bỏ vào DLQ, do chính broker ghi vào.
    // Chuỗi này có dạng: "Delivery[4] exceeds redelivery policy limit:RedeliveryPolicy {...}"
    // — vừa cho biết đã giao mấy lần, vừa cho biết chính sách nào đang áp dụng.
    //
    // KHÔNG lưu một con số "số lần giao lại" riêng: cả JMSXDeliveryCount lẫn
    // ActiveMQMessage.getRedeliveryCounter() đều KHÔNG mang giá trị gốc sang DLQ
    // (một cái đếm lần giao trong chính DLQ, một cái bị reset về 0) — lưu chúng thì ra
    // con số trông hợp lý nhưng vô nghĩa.
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

    // Mọi lỗi đọc đều nuốt lại: không ghi nổi nội dung thì vẫn phải ghi được rằng CÓ một message
    // chết. Ném lỗi ở đây là message quay lại DLQ và lặp vô hạn.
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
