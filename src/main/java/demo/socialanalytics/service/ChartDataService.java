package demo.socialanalytics.service;

import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.dto.response.PlatformSummaryResponse;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.util.PlatformParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChartDataService {

    public static final int DEFAULT_DAYS = 7;
    // Trần để một lời gọi không kéo về hàng nghìn điểm — biểu đồ cũng không đọc nổi.
    public static final int MAX_DAYS = 90;

    private final SocialMetricRepository metricRepository;
    private final StatisticsService statisticsService;

    public ChartDataService(
        SocialMetricRepository metricRepository, StatisticsService statisticsService) {
        this.metricRepository = metricRepository;
        this.statisticsService = statisticsService;
    }

    public ChartDataResponse chartData(String platform, Integer days) {
        int window = resolveDays(days);
        Platform parsed = PlatformParser.parse(platform);
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        // Native query nên phải truyền enum dưới dạng chuỗi.
        List<Object[]> rows = metricRepository.sumDailyTotals(
            from, parsed == null ? null : parsed.name());

        List<LocalDate> labels = new ArrayList<>();
        List<Long> likes = new ArrayList<>();
        List<Long> shares = new ArrayList<>();
        List<Long> comments = new ArrayList<>();
        List<Long> followers = new ArrayList<>();

        for (Object[] row : rows) {
            labels.add(toLocalDate(row[0]));
            likes.add(toLong(row[1]));
            shares.add(toLong(row[2]));
            comments.add(toLong(row[3]));
            followers.add(toLong(row[4]));
        }

        List<PlatformSummaryResponse> platforms = statisticsService.current()
            .stream().map(PlatformSummaryResponse::of).toList();

        return new ChartDataResponse(
            labels, likes, shares, comments, followers, platforms, LocalDateTime.now());
    }

    private int resolveDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new InvalidRequestParameterException(
                "days phải nằm trong khoảng 1 đến " + MAX_DAYS);
        }
        return days;
    }

    // Kiểu trả về của cột ngày khác nhau giữa các driver (java.sql.Date, LocalDate,
    // hoặc Timestamp), nên nhận diện từng kiểu thay vì ép cứng một kiểu.
    private LocalDate toLocalDate(Object value) {
        return switch (value) {
            case LocalDate date -> date;
            case Date date -> date.toLocalDate();
            case java.sql.Timestamp timestamp -> timestamp.toLocalDateTime().toLocalDate();
            case null -> null;
            default -> LocalDate.parse(String.valueOf(value));
        };
    }

    private Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
