package demo.socialanalytics.messaging;

import demo.socialanalytics.entity.DeadLetter;
import demo.socialanalytics.repository.DeadLetterRepository;
import demo.socialanalytics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "social.messaging.max-redeliveries=3",
    "social.messaging.initial-redelivery-delay-ms=50",
    "social.messaging.back-off-multiplier=1"
})
@ActiveProfiles("test")
class RetryAndDeadLetterTest {
    @Autowired JmsTemplate jmsTemplate;
    @Autowired DeadLetterRepository deadLetters;

    @MockitoBean StatisticsService statisticsService;

    private final AtomicInteger attempts = new AtomicInteger();

    @BeforeEach
    void setUp() {
        deadLetters.deleteAll();
        attempts.set(0);
    }

    private ImportCompletedMessage message() {
        return new ImportCompletedMessage(1L, 5, 3, 2, LocalDateTime.now());
    }

    private void alwaysFail() {
        when(statisticsService.refresh()).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("cố tình hỏng để kiểm tra cơ chế thử lại");
        });
    }

    @Test
    void nemLoiThiDuocGiaoLai() {
        alwaysFail();

        jmsTemplate.convertAndSend(Queues.IMPORT_COMPLETED, message());

        await().atMost(ofSeconds(15)).untilAsserted(() ->
            assertThat(attempts.get()).isEqualTo(4));
    }

    @Test
    void thuLaiHetSoLanThiVaoDlq() {
        alwaysFail();

        jmsTemplate.convertAndSend(Queues.IMPORT_COMPLETED, message());

        await().atMost(ofSeconds(20)).untilAsserted(() ->
            assertThat(deadLetters.findAll()).isNotEmpty());

        List<DeadLetter> recorded = deadLetters.findAll();
        assertThat(recorded).singleElement().satisfies(letter -> {
            assertThat(letter.getSourceQueue()).contains(Queues.IMPORT_COMPLETED);
            assertThat(letter.getPayload()).contains("\"imported\":3");
            assertThat(letter.getMessageId()).isNotBlank();
            assertThat(letter.getReceivedAt()).isNotNull();
            assertThat(letter.getFailureCause())
                .contains("Delivery[4]")
                .contains("exceeds redelivery policy limit");
        });
    }

    @Test
    void loiTamThoiThiThuLaiThanhCong() {
        when(statisticsService.refresh()).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("lỗi tạm thời");
            }
            return List.of();
        });

        jmsTemplate.convertAndSend(Queues.IMPORT_COMPLETED, message());

        await().atMost(ofSeconds(15)).untilAsserted(() ->
            assertThat(attempts.get()).isEqualTo(2));

        await().during(ofSeconds(2)).atMost(ofSeconds(6))
            .untilAsserted(() -> assertThat(deadLetters.findAll()).isEmpty());
        verify(statisticsService, times(2)).refresh();
    }

    @Test
    void xuLyBinhThuongKhongVaoDlq() {
        when(statisticsService.refresh()).thenReturn(List.of());

        jmsTemplate.convertAndSend(Queues.IMPORT_COMPLETED, message());

        await().atMost(ofSeconds(10)).untilAsserted(() ->
            verify(statisticsService, atLeastOnce()).refresh());
        await().during(ofSeconds(2)).atMost(ofSeconds(6))
            .untilAsserted(() -> assertThat(deadLetters.findAll()).isEmpty());
    }

    // Message hỏng ngay ở khâu chuyển đổi JSON cũng phải vào DLQ chứ không lặp vô hạn
    @Test
    void messageSaiDinhDangVaoDlq() {
        jmsTemplate.send(Queues.IMPORT_COMPLETED,
            session -> session.createTextMessage("{ đây không phải JSON hợp lệ"));

        await().atMost(ofSeconds(20)).untilAsserted(() ->
            assertThat(deadLetters.findAll()).isNotEmpty());

        assertThat(deadLetters.findAll()).first()
            .satisfies(letter -> assertThat(letter.getPayload()).contains("không phải JSON"));
        verify(statisticsService, never()).refresh();
    }
}
