package demo.socialanalytics.service;

import demo.socialanalytics.dto.request.PostRequest;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.dto.response.PostResponse;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.exception.DuplicateResourceException;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.mapper.MetricMapper;
import demo.socialanalytics.mapper.PostMapper;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock SocialMetricRepository metricRepository;

    PostService postService;

    User owner;
    Post existingPost;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, metricRepository,
            new PostMapper(new MetricMapper()));
        owner = TestEntities.user(1L, "admin@example.com");
        existingPost = TestEntities.post(10L, owner, Platform.FACEBOOK, "fb-001");
    }

    private PostRequest request(String platform, String externalId) {
        return new PostRequest(owner.getId(), platform, externalId, "Nội dung", "https://example.com/p", null);
    }

    @Nested
    @DisplayName("list()")
    class ListPosts {
        @Test
        void khongLocThiGoiFindAllVaTraVeThongTinPhanTrang() {
            Page<Post> page = new PageImpl<>(List.of(existingPost), PageRequest.of(0, 20), 1);
            when(postRepository.findAll(any(Pageable.class))).thenReturn(page);
            when(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

            PageResponse<PostResponse> result = postService.list(null, 1, 20);

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.data()).singleElement()
                .satisfies(post -> assertThat(post.externalId()).isEqualTo("fb-001"));
            verify(postRepository, never()).findByPlatform(any(), any());
        }

        @Test
        void coLocThiGoiFindByPlatform() {
            when(postRepository.findByPlatform(eq(Platform.TWITTER), any(Pageable.class)))
                .thenReturn(Page.empty());

            postService.list("twitter", 1, 20);

            verify(postRepository).findByPlatform(eq(Platform.TWITTER), any(Pageable.class));
            verify(postRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        void doiSoTrangTu1VeTu0TruocKhiGoiRepository() {
            when(postRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

            postService.list(null, 3, 15);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(postRepository).findAll(captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(15);
        }

        @Test
        void nenTangKhongHopLe() {
            assertThatThrownBy(() -> postService.list("instagram", 1, 20))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("instagram");

            verifyNoInteractions(postRepository);
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {
        @Test
        void kemTheoSoLieuCuaLanDoGanNhat() {
            SocialMetric latest = TestEntities.metric(99L, existingPost, 250, LocalDateTime.of(2026, 4, 1, 8, 0));
            when(postRepository.findWithUserById(10L)).thenReturn(Optional.of(existingPost));
            when(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(10L))
                .thenReturn(Optional.of(latest));

            PostResponse result = postService.getById(10L);

            assertThat(result.latestMetric()).isNotNull();
            assertThat(result.latestMetric().likes()).isEqualTo(250);
            assertThat(result.user().email()).isEqualTo("admin@example.com");
        }

        @Test
        void baiChuaCrawlLanNaoThiLatestMetricLaNull() {
            when(postRepository.findWithUserById(10L)).thenReturn(Optional.of(existingPost));
            when(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

            assertThat(postService.getById(10L).latestMetric()).isNull();
        }

        @Test
        void khongTimThay() {
            when(postRepository.findWithUserById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {
        @Test
        void luuBaiVietVoiDuLieuTuRequest() {
            when(postRepository.existsByPlatformAndExternalId(Platform.FACEBOOK, "fb-new")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
            when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
                Post saved = invocation.getArgument(0);
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 55L);
                return saved;
            });

            PostResponse result = postService.create(request("facebook", "fb-new"));

            ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
            verify(postRepository).save(captor.capture());
            assertThat(captor.getValue().getPlatform()).isEqualTo(Platform.FACEBOOK);
            assertThat(captor.getValue().getExternalId()).isEqualTo("fb-new");
            assertThat(captor.getValue().getUser()).isSameAs(owner);
            assertThat(result.id()).isEqualTo(55L);
            assertThat(result.latestMetric()).isNull();
            verifyNoInteractions(metricRepository);
        }

        @Test
        void trungCapPlatformVaExternalId() {
            when(postRepository.existsByPlatformAndExternalId(Platform.FACEBOOK, "fb-001")).thenReturn(true);

            assertThatThrownBy(() -> postService.create(request("facebook", "fb-001")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("fb-001");

            verify(postRepository, never()).save(any());
            verifyNoInteractions(userRepository);
        }

        @Test
        void nguoiDungKhongTonTai() {
            when(postRepository.existsByPlatformAndExternalId(any(), any())).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.create(request("facebook", "fb-new")))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(postRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {
        @Test
        void giuNguyenCapDinhDanhThiKhongCanKiemTraTrung() {
            when(postRepository.findWithUserById(10L)).thenReturn(Optional.of(existingPost));
            when(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

            PostResponse result = postService.update(10L,
                new PostRequest(1L, "facebook", "fb-001", "Nội dung mới", "https://example.com/x", null));

            assertThat(result.content()).isEqualTo("Nội dung mới");
            verify(postRepository, never()).existsByPlatformAndExternalId(any(), any());
            verify(postRepository, never()).save(any());
        }

        @Test
        void doiSangCapDinhDanhDaThuocBaiKhacThiBaoLoi() {
            when(postRepository.findWithUserById(10L)).thenReturn(Optional.of(existingPost));
            when(postRepository.existsByPlatformAndExternalId(Platform.TWITTER, "tw-999")).thenReturn(true);

            assertThatThrownBy(() -> postService.update(10L, request("twitter", "tw-999")))
                .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        void doiChuSoHuuThiNapNguoiDungMoi() {
            User newOwner = TestEntities.user(2L, "other@example.com");
            when(postRepository.findWithUserById(10L)).thenReturn(Optional.of(existingPost));
            when(userRepository.findById(2L)).thenReturn(Optional.of(newOwner));
            when(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

            PostResponse result = postService.update(10L,
                new PostRequest(2L, "facebook", "fb-001", "Nội dung", null, null));

            assertThat(result.user().email()).isEqualTo("other@example.com");
        }

        @Test
        void khongTimThay() {
            when(postRepository.findWithUserById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.update(404L, request("facebook", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {
        @Test
        void xoaBaiVietTimDuoc() {
            when(postRepository.findById(10L)).thenReturn(Optional.of(existingPost));

            postService.delete(10L);

            verify(postRepository).delete(existingPost);
        }

        @Test
        void khongTimThay() {
            when(postRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(postRepository, never()).delete(any());
        }
    }
}
