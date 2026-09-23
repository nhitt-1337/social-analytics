package demo.socialanalytics.service;

import demo.socialanalytics.dto.request.MetricRequest;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.mapper.MetricMapper;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {
    @Mock SocialMetricRepository metricRepository;
    @Mock PostRepository postRepository;

    MetricService metricService;

    Post post;

    @BeforeEach
    void setUp() {
        metricService = new MetricService(metricRepository, postRepository, new MetricMapper());
        User owner = TestEntities.user(1L, "admin@example.com");
        post = TestEntities.post(10L, owner, Platform.FACEBOOK, "fb-001");
    }

    @Nested
    @DisplayName("listByPost()")
    class ListByPost {
        @Test
        void traVeLichSuDoSapMoiNhatTruoc() {
            SocialMetric metric = TestEntities.metric(1L, post, 100, LocalDateTime.of(2026, 4, 1, 10, 0));
            Page<SocialMetric> page = new PageImpl<>(List.of(metric), PageRequest.of(0, 20), 1);
            when(postRepository.existsById(10L)).thenReturn(true);
            when(metricRepository.findByPostId(eq(10L), any(Pageable.class))).thenReturn(page);

            var result = metricService.listByPost(10L, 1, 20);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.data()).singleElement()
                .satisfies(item -> assertThat(item.likes()).isEqualTo(100));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(metricRepository).findByPostId(eq(10L), captor.capture());
            assertThat(captor.getValue().getSort().getOrderFor("collectedAt").isDescending()).isTrue();
        }

        @Test
        void baiVietKhongTonTai() {
            when(postRepository.existsById(404L)).thenReturn(false);

            assertThatThrownBy(() -> metricService.listByPost(404L, 1, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("bài viết");

            verifyNoInteractions(metricRepository);
        }
    }

    @Nested
    @DisplayName("timeSeries()")
    class TimeSeries {
        @Test
        void dungDungKhoangThoiGianNguoiDungTruyen() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 2, 1, 0, 0);
            when(postRepository.existsById(10L)).thenReturn(true);
            when(metricRepository.findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(10L, from, to))
                .thenReturn(List.of(TestEntities.metric(1L, post, 5, from)));

            ListResponse<MetricResponse> result = metricService.timeSeries(10L, from, to);

            assertThat(result.data()).hasSize(1);
            verify(metricRepository).findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(10L, from, to);
        }

        @Test
        void boTrongThiLay30NgayGanNhat() {
            when(postRepository.existsById(10L)).thenReturn(true);
            when(metricRepository.findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
                eq(10L), any(), any())).thenReturn(List.of());

            LocalDateTime before = LocalDateTime.now();
            metricService.timeSeries(10L, null, null);

            ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(metricRepository).findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
                eq(10L), fromCaptor.capture(), toCaptor.capture());

            assertThat(toCaptor.getValue()).isAfterOrEqualTo(before);
            assertThat(java.time.Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toDays())
                .isEqualTo(30);
        }

        @Test
        void chiTruyenFromThiToLaHienTai() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
            when(postRepository.existsById(10L)).thenReturn(true);
            when(metricRepository.findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
                eq(10L), eq(from), any())).thenReturn(List.of());

            LocalDateTime before = LocalDateTime.now();
            metricService.timeSeries(10L, from, null);

            ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(metricRepository).findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
                eq(10L), eq(from), toCaptor.capture());
            assertThat(toCaptor.getValue()).isAfterOrEqualTo(before);
        }

        @Test
        void khongTimThayBaiViet() {
            when(postRepository.existsById(404L)).thenReturn(false);

            assertThatThrownBy(() -> metricService.timeSeries(404L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("record() — background job crawl gọi vào đây")
    class Record {
        @Test
        void luuLanDoVoiDuLieuTuRequest() {
            LocalDateTime collectedAt = LocalDateTime.of(2026, 4, 2, 7, 0);
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(metricRepository.save(any(SocialMetric.class))).thenAnswer(invocation -> {
                SocialMetric saved = invocation.getArgument(0);
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 7L);
                return saved;
            });

            MetricResponse result = metricService.record(
                new MetricRequest(10L, 300, 40, 12, 5_000, collectedAt));

            ArgumentCaptor<SocialMetric> captor = ArgumentCaptor.forClass(SocialMetric.class);
            verify(metricRepository).save(captor.capture());
            SocialMetric saved = captor.getValue();
            assertThat(saved.getPost()).isSameAs(post);
            assertThat(saved.getLikes()).isEqualTo(300);
            assertThat(saved.getShares()).isEqualTo(40);
            assertThat(saved.getFollowers()).isEqualTo(5_000);
            assertThat(saved.getCollectedAt()).isEqualTo(collectedAt);
            assertThat(result.id()).isEqualTo(7L);
            assertThat(result.postId()).isEqualTo(10L);
        }

        // comments cho phép bỏ trống nhưng cột trong DB là NOT NULL, phải quy về 0
        @Test
        void commentsBoTrongThiQuyVe0() {
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(metricRepository.save(any(SocialMetric.class))).thenAnswer(i -> i.getArgument(0));

            metricService.record(new MetricRequest(10L, 1, 1, null, 1, null));

            ArgumentCaptor<SocialMetric> captor = ArgumentCaptor.forClass(SocialMetric.class);
            verify(metricRepository).save(captor.capture());
            assertThat(captor.getValue().getComments()).isZero();
        }

        @Test
        void baiVietKhongTonTai() {
            when(postRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> metricService.record(
                new MetricRequest(404L, 1, 1, 1, 1, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("bài viết");

            verify(metricRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getById() và delete()")
    class GetAndDelete {
        @Test
        void getByIdTraVeLanDoTimDuoc() {
            SocialMetric metric = TestEntities.metric(7L, post, 88, LocalDateTime.of(2026, 4, 1, 9, 0));
            when(metricRepository.findById(7L)).thenReturn(Optional.of(metric));

            assertThat(metricService.getById(7L).likes()).isEqualTo(88);
        }

        @Test
        void getByIdKhongTimThayThiBaoLoi() {
            when(metricRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> metricService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("chỉ số");
        }

        @Test
        void deleteXoaLanDoTimDuoc() {
            SocialMetric metric = TestEntities.metric(7L, post, 88, LocalDateTime.now());
            when(metricRepository.findById(7L)).thenReturn(Optional.of(metric));

            metricService.delete(7L);

            verify(metricRepository).delete(metric);
        }

        @Test
        void deleteKhongTimThay() {
            when(metricRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> metricService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(metricRepository, never()).delete(any());
        }
    }
}
