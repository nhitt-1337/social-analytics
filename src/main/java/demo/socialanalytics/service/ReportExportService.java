package demo.socialanalytics.service;

import demo.socialanalytics.dto.excel.PostReportRow;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.excel.ExcelMapper;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.util.PlatformParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Xuất báo cáo bài viết kèm số liệu mới nhất ra file Excel.
@Service
@Transactional(readOnly = true)
public class ReportExportService {

    // Trần số dòng cho một lần xuất. Quá ngưỡng thì người dùng nên thu hẹp khoảng thời gian.
    public static final int MAX_EXPORT_ROWS = 10_000;

    private static final String SHEET_NAME = "Bao cao";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PostRepository postRepository;
    private final SocialMetricRepository metricRepository;
    private final ExcelMapper excelMapper;

    public ReportExportService(
        PostRepository postRepository,
        SocialMetricRepository metricRepository,
        ExcelMapper excelMapper
    ) {
        this.postRepository = postRepository;
        this.metricRepository = metricRepository;
        this.excelMapper = excelMapper;
    }

    public byte[] exportReport(String platform, LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestParameterException("from phải trước to");
        }
        Platform parsed = PlatformParser.parse(platform);

        List<Post> posts = postRepository.findForReport(
            parsed, from, to, PageRequest.ofSize(MAX_EXPORT_ROWS));

        Map<Long, SocialMetric> latestByPost = loadLatestMetrics(posts);

        List<PostReportRow> rows = posts.stream()
            .map(post -> toRow(post, latestByPost.get(post.getId())))
            .toList();

        return excelMapper.write(rows, PostReportRow.class, SHEET_NAME);
    }

    // Tên file có mốc thời gian để tải nhiều lần không đè lên nhau.
    public String buildFileName() {
        return "bao-cao-tuong-tac-" + LocalDateTime.now().format(FILE_STAMP) + ".xlsx";
    }

    // Một truy vấn cho tất cả bài; kết quả đã sắp giảm dần nên bản ghi ĐẦU TIÊN của mỗi
    // postId chính là lần đo gần nhất -> putIfAbsent là đủ.
    private Map<Long, SocialMetric> loadLatestMetrics(List<Post> posts) {
        Map<Long, SocialMetric> latest = new HashMap<>();
        if (posts.isEmpty()) {
            return latest;
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        for (SocialMetric metric : metricRepository.findByPostIdInOrderByCollectedAtDesc(postIds)) {
            latest.putIfAbsent(metric.getPost().getId(), metric);
        }
        return latest;
    }

    // metric null khi bài chưa được crawl lần nào -> các cột chỉ số để trống thay vì ghi 0,
    // tránh nhầm "chưa đo" với "đo được 0".
    private PostReportRow toRow(Post post, SocialMetric metric) {
        return new PostReportRow(
            post.getId(),
            post.getPlatform().getSlug(),
            post.getExternalId(),
            post.getContent(),
            post.getUrl(),
            post.getPostedAt(),
            post.getUser().getFullName(),
            post.getUser().getEmail(),
            metric == null ? null : metric.getLikes(),
            metric == null ? null : metric.getShares(),
            metric == null ? null : metric.getComments(),
            metric == null ? null : metric.getFollowers(),
            metric == null ? null : metric.getCollectedAt()
        );
    }
}
