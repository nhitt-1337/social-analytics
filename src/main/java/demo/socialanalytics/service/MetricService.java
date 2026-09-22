package demo.socialanalytics.service;

import demo.socialanalytics.dto.request.MetricRequest;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.mapper.MetricMapper;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MetricService {

    private final SocialMetricRepository metricRepository;
    private final PostRepository postRepository;
    private final MetricMapper metricMapper;

    public MetricService(
        SocialMetricRepository metricRepository,
        PostRepository postRepository,
        MetricMapper metricMapper
    ) {
        this.metricRepository = metricRepository;
        this.postRepository = postRepository;
        this.metricMapper = metricMapper;
    }

    public PageResponse<MetricResponse> listByPost(Long postId, int page, int limit) {
        requirePost(postId);
        Pageable pageable = PageRequest.of(page - 1, limit,
            Sort.by(Sort.Direction.DESC, "collectedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(metricRepository.findByPostId(postId, pageable).map(metricMapper::toResponse));
    }

    // Chuỗi thời gian cho biểu đồ: không phân trang, sắp tăng dần theo thời điểm đo.
    // Bỏ trống from/to thì lấy 30 ngày gần nhất.
    public ListResponse<MetricResponse> timeSeries(Long postId, LocalDateTime from, LocalDateTime to) {
        requirePost(postId);
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.minusDays(30);
        return ListResponse.of(
            metricRepository.findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(postId, start, end)
                .stream().map(metricMapper::toResponse).toList()
        );
    }

    public MetricResponse getById(Long id) {
        return metricMapper.toResponse(metricRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("chỉ số")));
    }

    // Background job crawl ở bước sau sẽ gọi đúng method này.
    @Transactional
    public MetricResponse record(MetricRequest request) {
        Post post = postRepository.findById(request.postId())
            .orElseThrow(() -> new ResourceNotFoundException("bài viết"));

        SocialMetric metric = new SocialMetric();
        metric.setPost(post);
        metric.setLikes(request.likes());
        metric.setShares(request.shares());
        metric.setComments(request.comments() == null ? 0 : request.comments());
        metric.setFollowers(request.followers());
        metric.setCollectedAt(request.collectedAt());

        return metricMapper.toResponse(metricRepository.save(metric));
    }

    @Transactional
    public void delete(Long id) {
        SocialMetric metric = metricRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("chỉ số"));
        metricRepository.delete(metric);
    }

    private void requirePost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("bài viết");
        }
    }
}
