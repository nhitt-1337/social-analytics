package demo.socialanalytics.mapper;

import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.dto.response.PostResponse;
import demo.socialanalytics.dto.response.UserSummaryResponse;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    private final MetricMapper metricMapper;

    public PostMapper(MetricMapper metricMapper) {
        this.metricMapper = metricMapper;
    }

    // latestMetric do service truyền vào (null nếu bài chưa có lần đo nào) thay vì đọc
    public PostResponse toResponse(Post post, SocialMetric latestMetric) {
        MetricResponse metric = latestMetric == null ? null : metricMapper.toResponse(latestMetric);
        return new PostResponse(
            post.getId(),
            post.getPlatform().getSlug(),
            post.getExternalId(),
            post.getContent(),
            post.getUrl(),
            post.getPostedAt(),
            new UserSummaryResponse(
                post.getUser().getId(),
                post.getUser().getFullName(),
                post.getUser().getEmail()
            ),
            metric,
            post.getCreatedAt()
        );
    }
}
