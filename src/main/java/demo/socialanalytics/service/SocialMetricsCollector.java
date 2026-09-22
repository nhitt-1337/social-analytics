package demo.socialanalytics.service;

import demo.socialanalytics.client.SocialApiClient;
import demo.socialanalytics.client.SocialMetricsSnapshot;
import demo.socialanalytics.config.AsyncConfig;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Crawl chỉ số cho MỘT tài khoản, chạy trên luồng nền.
//
// Đơn vị chạy song song là tài khoản chứ không phải bài viết: giới hạn tần suất của Facebook/X
// tính theo token, nên gọi dồn nhiều bài của cùng một tài khoản cùng lúc là cách nhanh nhất
// để bị chặn. Nhiều tài khoản chạy song song, trong một tài khoản thì tuần tự.
@Service
public class SocialMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(SocialMetricsCollector.class);

    private final PostRepository postRepository;
    private final SocialApiClient socialApiClient;
    private final MetricWriter metricWriter;

    public SocialMetricsCollector(
        PostRepository postRepository,
        SocialApiClient socialApiClient,
        MetricWriter metricWriter
    ) {
        this.postRepository = postRepository;
        this.socialApiClient = socialApiClient;
        this.metricWriter = metricWriter;
    }

    // Trả CompletableFuture chứ không phải void, vì job cần BIẾT khi nào xong và kết quả ra sao.
    // @Async trên method void thì lỗi ném ra sẽ không có đường quay lại luồng gọi.
    //
    // Method này KHÔNG @Transactional: một transaction mở suốt cả lượt crawl sẽ giữ kết nối DB
    // trong lúc ngồi chờ mạng. Việc ghi giao cho MetricWriter, mỗi bài một transaction ngắn.
    @Async(AsyncConfig.CRAWL_EXECUTOR)
    public CompletableFuture<AccountCrawlResult> collectForAccount(Long userId) {
        List<Post> posts = loadPosts(userId);
        if (posts.isEmpty()) {
            return CompletableFuture.completedFuture(AccountCrawlResult.empty(userId));
        }

        log.debug("Bắt đầu crawl tài khoản {} với {} bài viết", userId, posts.size());
        int succeeded = 0;
        int failed = 0;

        for (Post post : posts) {
            if (Thread.currentThread().isInterrupted()) {
                // Ứng dụng đang tắt hoặc lần chạy đã quá hạn: dừng sớm, phần đã ghi vẫn giữ.
                log.warn("Dừng sớm khi crawl tài khoản {}: luồng bị ngắt", userId);
                break;
            }
            if (crawlOnePost(post)) {
                succeeded++;
            } else {
                failed++;
            }
        }

        log.debug("Xong tài khoản {}: {} thành công, {} lỗi", userId, succeeded, failed);
        return CompletableFuture.completedFuture(
            new AccountCrawlResult(userId, posts.size(), succeeded, failed));
    }

    // Lỗi của MỘT bài chỉ dừng lại ở bài đó. Bắt cả Exception chứ không riêng SocialApiException
    // vì lỗi ghi DB cũng không được phép làm hỏng phần còn lại của tài khoản.
    private boolean crawlOnePost(Post post) {
        try {
            SocialMetricsSnapshot snapshot = socialApiClient.fetchMetrics(post);
            metricWriter.record(post.getId(), snapshot, LocalDateTime.now());
            return true;
        } catch (Exception exception) {
            // Ghi cả stack trace: đây là luồng nền, không có ai nhìn thấy lỗi ngoài log.
            log.warn("Không crawl được bài {} ({}): {}",
                post.getId(), post.getExternalId(), exception.getMessage(), exception);
            return false;
        }
    }

    // Không đánh @Transactional ở đây: gọi từ trong cùng class thì không đi qua proxy nên
    // annotation cũng vô tác dụng. Method của Spring Data repository tự có transaction riêng,
    // và ở đây chỉ cần đọc một lần nên thế là đủ.
    private List<Post> loadPosts(Long userId) {
        return postRepository.findByUserIdOrderByIdAsc(userId);
    }
}
