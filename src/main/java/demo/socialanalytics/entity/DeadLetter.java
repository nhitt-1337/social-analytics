package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Message đã thử lại hết số lần cho phép mà vẫn hỏng, bị broker đẩy sang DLQ.
//
// Lưu xuống DB vì DLQ trên broker là thứ không ai nhìn tới: message nằm đó im lặng cho tới khi
// hàng đợi đầy. Có bảng này thì dashboard hiện được, và còn nội dung gốc để xử lý lại sau.
@Getter
@Entity
@Table(
    name = "dead_letters",
    indexes = @Index(name = "idx_dead_letter_received_at", columnList = "received_at")
)
public class DeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_queue", nullable = false, length = 200)
    @Setter
    private String sourceQueue;

    @Column(name = "message_id", length = 200)
    @Setter
    private String messageId;

    // Nội dung gốc, giữ nguyên để có thể gửi lại sau khi đã sửa nguyên nhân.
    @Column(nullable = false, columnDefinition = "text")
    @Setter
    private String payload;

    // Lý do broker bỏ cuộc, do chính broker ghi vào message.
    // Dạng: "Delivery[4] exceeds redelivery policy limit:RedeliveryPolicy {...}"
    @Column(name = "failure_cause", length = 2100)
    @Setter
    private String failureCause;

    @Column(name = "received_at", nullable = false)
    @Setter
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
