package demo.socialanalytics.service;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.PlatformSummary;
import demo.socialanalytics.repository.PlatformSummaryRepository;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.projection.PlatformCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Dựng lại bảng thống kê tổng hợp từ dữ liệu bài viết.
@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    private final PostRepository postRepository;
    private final PlatformSummaryRepository summaryRepository;

    public StatisticsService(PostRepository postRepository, PlatformSummaryRepository summaryRepository) {
        this.postRepository = postRepository;
        this.summaryRepository = summaryRepository;
    }

    // TÍNH LẠI TỪ ĐẦU chứ không cộng dồn vào số cũ.
    //
    // Nhờ vậy việc xử lý cùng một message hai lần cho ra đúng một kết quả — điều bắt buộc với
    // hàng đợi, vì JMS chỉ bảo đảm "ít nhất một lần": broker giao lại khi listener đang xử lý
    // dở mà mất kết nối, dù lần trước có thể đã chạy xong.
    @Transactional
    public List<PlatformSummary> refresh() {
        Map<Platform, PlatformCount> counts = new EnumMap<>(Platform.class);
        postRepository.countGroupedByPlatform()
            .forEach(count -> counts.put(count.platform(), count));

        LocalDateTime now = LocalDateTime.now();
        List<PlatformSummary> result = new java.util.ArrayList<>();

        // Duyệt qua MỌI nền tảng, không chỉ những nền tảng đang có bài: xoá hết bài của một nền
        // tảng thì số liệu của nó phải về 0, chứ không được giữ nguyên con số cũ.
        for (Platform platform : Platform.values()) {
            PlatformSummary summary = summaryRepository.findByPlatform(platform)
                .orElseGet(() -> {
                    PlatformSummary created = new PlatformSummary();
                    created.setPlatform(platform);
                    return created;
                });

            PlatformCount count = counts.get(platform);
            summary.setPostCount(count == null ? 0 : count.postCount());
            summary.setAccountCount(count == null ? 0 : count.accountCount());
            summary.setUpdatedAt(now);
            result.add(summaryRepository.save(summary));
        }

        log.info("Đã cập nhật thống kê tổng hợp: {}",
            result.stream().map(s -> s.getPlatform().getSlug() + "=" + s.getPostCount()).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public List<PlatformSummary> current() {
        return summaryRepository.findAllByOrderByPlatformAsc();
    }
}
