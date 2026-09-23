package demo.socialanalytics.messaging;

// Tên các hàng đợi, gom một chỗ để producer và listener không bao giờ gõ lệch nhau.
public final class Queues {

    // Phát khi một lần import Excel kết thúc.
    public static final String IMPORT_COMPLETED = "import.completed";

    // Hàng đợi thư chết của ActiveMQ. Broker tự chuyển message sang đây sau khi đã thử lại
    // đủ số lần cấu hình trong RedeliveryPolicy.
    //
    // Đây là DLQ dùng CHUNG cho mọi hàng đợi — mặc định của ActiveMQ Classic. Muốn mỗi hàng đợi
    // một DLQ riêng thì phải đổi individualDeadLetterStrategy ở phía broker.
    public static final String DEAD_LETTER = "ActiveMQ.DLQ";

    private Queues() {
    }
}
