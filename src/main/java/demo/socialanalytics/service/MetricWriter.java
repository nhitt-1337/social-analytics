package demo.socialanalytics.service;

import demo.socialanalytics.client.SocialMetricsSnapshot;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Ghi một lần đo xuống DB.
//
// Tách thành bean riêng vì @Transactional chỉ có tác dụng khi được gọi TỪ BÊN NGOÀI qua proxy;
// gọi thẳng một method @Transactional trong cùng class thì Spring không chen vào được và
// transaction không hề mở.
//
// Mỗi bài là một transaction riêng (REQUIRES_NEW): bài thứ 5 lỗi thì 4 bài trước vẫn giữ nguyên
// kết quả, thay vì cuốn cả lượt crawl của tài khoản đó vào một lần rollback.
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
