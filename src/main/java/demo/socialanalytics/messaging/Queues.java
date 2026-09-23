package demo.socialanalytics.messaging;

// Tên các hàng đợi, gom một chỗ để producer và listener không bao giờ gõ lệch nhau.
public final class Queues {

    // Phát khi một lần import Excel kết thúc.
    public static final String IMPORT_COMPLETED = "import.completed";

    // Hàng đợi thư chết của ActiveMQ. Broker tự chuyển message sang đây sau khi đã thử lại
    public static final String DEAD_LETTER = "ActiveMQ.DLQ";

    private Queues() {
    }
}
