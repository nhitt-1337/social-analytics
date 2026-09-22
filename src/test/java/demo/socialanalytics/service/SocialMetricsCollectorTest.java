package demo.socialanalytics.service;

import demo.socialanalytics.client.SocialApiClient;
import demo.socialanalytics.client.SocialApiException;
import demo.socialanalytics.client.SocialMetricsSnapshot;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Xử lý lỗi trong luồng nền là phần dễ sai nhất: nuốt lỗi thì mất dấu, để lỗi thoát ra thì
// một bài hỏng kéo sập cả tài khoản. Đây là chỗ kiểm ranh giới đó.
//
// Gọi trực tiếp method (không qua proxy Spring) nên @Async không kích hoạt — đúng ý: test này
// chỉ quan tâm logic, phần chạy song song được kiểm ở SocialMetricsUpdateJobTest.
@ExtendWith(MockitoExtension.class)
class SocialMetricsCollectorTest {

    @Mock PostRepository postRepository;
    @Mock SocialApiClient socialApiClient;
    @Mock MetricWriter metricWriter;

    SocialMetricsCollector collector;
    User owner;

    @BeforeEach
    void setUp() {
        collector = new SocialMetricsCollector(postRepository, socialApiClient, metricWriter);
        owner = TestEntities.user(1L, "admin@example.com");
    }

    private Post post(long id, String externalId) {
        return TestEntities.post(id, owner, Platform.FACEBOOK, externalId);
    }

    private SocialMetricsSnapshot snapshot(int likes) {
        return new SocialMetricsSnapshot(likes, likes / 2, likes / 4, 1_000);
    }

    @Test
    void ghiMotLanDoChoMoiBaiViet() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L))
            .thenReturn(List.of(post(10L, "fb-1"), post(11L, "fb-2")));
        when(socialApiClient.fetchMetrics(any())).thenReturn(snapshot(100));

        AccountCrawlResult result = collector.collectForAccount(1L).get();

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.totalPosts()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(metricWriter, times(2)).record(anyLong(), any(), any());
    }

    @Test
    void chuyenDungSoLieuLayVeXuongTangGhi() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(post(10L, "fb-1")));
        when(socialApiClient.fetchMetrics(any()))
            .thenReturn(new SocialMetricsSnapshot(500, 40, 12, 9_000));

        collector.collectForAccount(1L).get();

        ArgumentCaptor<Long> postId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<SocialMetricsSnapshot> captured = ArgumentCaptor.forClass(SocialMetricsSnapshot.class);
        verify(metricWriter).record(postId.capture(), captured.capture(), any(LocalDateTime.class));
        assertThat(postId.getValue()).isEqualTo(10L);
        assertThat(captured.getValue().likes()).isEqualTo(500);
        assertThat(captured.getValue().followers()).isEqualTo(9_000);
    }

    // Bài thứ hai lỗi thì bài thứ ba VẪN phải được crawl.
    @Test
    void motBaiLoiKhongLamDungCaTaiKhoan() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L))
            .thenReturn(List.of(post(10L, "fb-1"), post(11L, "fb-2"), post(12L, "fb-3")));
        when(socialApiClient.fetchMetrics(any()))
            .thenReturn(snapshot(100))
            .thenThrow(new SocialApiException("nhà cung cấp trả 503"))
            .thenReturn(snapshot(300));

        AccountCrawlResult result = collector.collectForAccount(1L).get();

        assertThat(result.totalPosts()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.hasFailure()).isTrue();
        verify(metricWriter, times(2)).record(anyLong(), any(), any());
    }

    // Lỗi khi GHI DB cũng phải được bắt, không chỉ lỗi gọi API.
    @Test
    void loiGhiDatabaseCungDuocBatVaTinhLaThatBai() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L))
            .thenReturn(List.of(post(10L, "fb-1"), post(11L, "fb-2")));
        when(socialApiClient.fetchMetrics(any())).thenReturn(snapshot(100));
        // record() trả về SocialMetric nên dùng doReturn cho lần gọi thứ hai, không phải doNothing.
        doThrow(new RuntimeException("mất kết nối DB"))
            .doReturn(null)
            .when(metricWriter).record(anyLong(), any(), any());

        AccountCrawlResult result = collector.collectForAccount(1L).get();

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    // Future phải hoàn tất BÌNH THƯỜNG kể cả khi mọi bài đều lỗi: job dùng
    // CompletableFuture.allOf, một future hỏng sẽ kéo theo cả lô.
    @Test
    void futureVanHoanTatBinhThuongDuMoiBaiDeuLoi() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(post(10L, "fb-1")));
        when(socialApiClient.fetchMetrics(any())).thenThrow(new SocialApiException("hỏng"));

        var future = collector.collectForAccount(1L);

        assertThat(future.isCompletedExceptionally()).isFalse();
        assertThat(future.get().failed()).isEqualTo(1);
        assertThat(future.get().succeeded()).isZero();
    }

    @Test
    void taiKhoanKhongCoBaiNaoThiKhongGoiApi() throws Exception {
        when(postRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        AccountCrawlResult result = collector.collectForAccount(1L).get();

        assertThat(result.totalPosts()).isZero();
        assertThat(result.hasFailure()).isFalse();
        verifyNoInteractions(socialApiClient, metricWriter);
    }
}
