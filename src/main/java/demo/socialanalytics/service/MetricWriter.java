package demo.socialanalytics.service;

import demo.socialanalytics.client.SocialMetricsSnapshot;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// @Transactional chỉ có tác dụng khi gọi từ bên ngoài qua proxy
@Service
public class MetricWriter {

    private final SocialMetricRepository metricRepository;
    private final PostRepository postRepository;

    public MetricWriter(SocialMetricRepository metricRepository, PostRepository postRepository) {
        this.metricRepository = metricRepository;
        this.postRepository = postRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SocialMetric record(Long postId, SocialMetricsSnapshot snapshot, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        // getReferenceById: chỉ cần khoá ngoại, không cần nạp lại cả bài viết từ DB.
        metric.setPost(postRepository.getReferenceById(postId));
        metric.setLikes(snapshot.likes());
        metric.setShares(snapshot.shares());
        metric.setComments(snapshot.comments());
        metric.setFollowers(snapshot.followers());
        metric.setCollectedAt(collectedAt);
        return metricRepository.save(metric);
    }
}
