package demo.socialanalytics.messaging;

// Sự kiện NỘI BỘ trong ứng dụng, phát ra ngay trong transaction của lần import.
public record ImportCompletedEvent(ImportCompletedMessage message) {
}
