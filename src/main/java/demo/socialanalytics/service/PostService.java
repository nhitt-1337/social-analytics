package demo.socialanalytics.service;

import demo.socialanalytics.dto.request.PostRequest;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.dto.response.PostResponse;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.exception.DuplicateResourceException;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.mapper.PostMapper;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.repository.UserRepository;
import demo.socialanalytics.util.PlatformParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SocialMetricRepository metricRepository;
    private final PostMapper postMapper;

    public PostService(
        PostRepository postRepository,
        UserRepository userRepository,
        SocialMetricRepository metricRepository,
        PostMapper postMapper
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.metricRepository = metricRepository;
        this.postMapper = postMapper;
    }

    // Lọc theo nền tảng (tuỳ chọn) + phân trang ở DB, mới nhất trước.
    public PageResponse<PostResponse> list(String platform, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Platform parsed = PlatformParser.parse(platform);

        Page<Post> result = parsed == null
            ? postRepository.findAll(pageable)
            : postRepository.findByPlatform(parsed, pageable);

        return PageResponse.of(result.map(this::toResponseWithLatestMetric));
    }

    public PostResponse getById(Long id) {
        Post post = postRepository.findWithUserById(id)
            .orElseThrow(() -> new ResourceNotFoundException("bài viết"));
        return toResponseWithLatestMetric(post);
    }

    @Transactional
    public PostResponse create(PostRequest request) {
        Platform platform = PlatformParser.parse(request.platform());
        // Kiểm trước cho message rõ ràng; unique constraint ở DB vẫn là chốt chặn cuối.
        if (postRepository.existsByPlatformAndExternalId(platform, request.externalId())) {
            throw new DuplicateResourceException(
                "Bài viết " + request.externalId() + " trên " + platform.getSlug() + " đã tồn tại");
        }
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException("người dùng"));

        Post post = new Post();
        post.setUser(user);
        post.setPlatform(platform);
        post.setExternalId(request.externalId());
        post.setContent(request.content());
        post.setUrl(request.url());
        post.setPostedAt(request.postedAt());

        return postMapper.toResponse(postRepository.save(post), null);
    }

    @Transactional
    public PostResponse update(Long id, PostRequest request) {
        Post post = postRepository.findWithUserById(id)
            .orElseThrow(() -> new ResourceNotFoundException("bài viết"));
        Platform platform = PlatformParser.parse(request.platform());

        // Đổi sang cặp (platform, externalId) đã thuộc về bài khác -> 409.
        boolean identityChanged = platform != post.getPlatform()
            || !request.externalId().equals(post.getExternalId());
        if (identityChanged && postRepository.existsByPlatformAndExternalId(platform, request.externalId())) {
            throw new DuplicateResourceException(
                "Bài viết " + request.externalId() + " trên " + platform.getSlug() + " đã tồn tại");
        }

        if (!request.userId().equals(post.getUser().getId())) {
            post.setUser(userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("người dùng")));
        }
        post.setPlatform(platform);
        post.setExternalId(request.externalId());
        post.setContent(request.content());
        post.setUrl(request.url());
        post.setPostedAt(request.postedAt());

        return toResponseWithLatestMetric(post);
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("bài viết"));
        // orphanRemoval trên Post.metrics xoá luôn lịch sử chỉ số.
        postRepository.delete(post);
    }

    private PostResponse toResponseWithLatestMetric(Post post) {
        SocialMetric latest = metricRepository
            .findFirstByPostIdOrderByCollectedAtDescIdDesc(post.getId())
            .orElse(null);
        return postMapper.toResponse(post, latest);
    }
}
