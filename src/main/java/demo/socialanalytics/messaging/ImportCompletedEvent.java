package demo.socialanalytics.messaging;

// Sự kiện NỘI BỘ trong ứng dụng, phát ra ngay trong transaction của lần import.
//
// Không gửi thẳng JMS từ trong transaction: xem ImportCompletedProducer để biết lý do.
public record ImportCompletedEvent(ImportCompletedMessage message) {
}
