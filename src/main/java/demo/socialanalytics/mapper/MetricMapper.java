package demo.socialanalytics.mapper;

import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.entity.SocialMetric;
import org.springframework.stereotype.Component;

@Component
public class MetricMapper {

    public MetricResponse toResponse(SocialMetric metric) {
        return new MetricResponse(
            metric.getId(),
            metric.getPost().getId(),
            metric.getLikes(),
            metric.getShares(),
            metric.getComments(),
            metric.getFollowers(),
            metric.getCollectedAt()
        );
    }
}
