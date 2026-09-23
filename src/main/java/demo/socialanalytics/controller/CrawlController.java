package demo.socialanalytics.controller;

import demo.socialanalytics.dto.response.CrawlRunResponse;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.entity.CrawlRun;
import demo.socialanalytics.service.CrawlStatusService;
import demo.socialanalytics.service.SocialMetricsUpdateJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Crawl", description = "Trạng thái job cập nhật chỉ số")
@RestController
@RequestMapping("/crawl")
public class CrawlController {

    private final CrawlStatusService crawlStatusService;
    // Job tắt thì bean không tồn tại, nhưng endpoint xem trạng thái vẫn phải dùng được
    private final ObjectProvider<SocialMetricsUpdateJob> job;

    public CrawlController(CrawlStatusService crawlStatusService, ObjectProvider<SocialMetricsUpdateJob> job) {
        this.crawlStatusService = crawlStatusService;
        this.job = job;
    }

    @Operation(summary = "Lần cập nhật gần nhất",
        description = "204 khi job chưa chạy lần nào")
    @GetMapping("/last-run")
    public ResponseEntity<CrawlRunResponse> lastRun() {
        return crawlStatusService.lastRun()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "10 lần chạy gần nhất")
    @GetMapping("/runs")
    public ResponseEntity<ListResponse<CrawlRunResponse>> recentRuns() {
        return ResponseEntity.ok(ListResponse.of(crawlStatusService.recentRuns()));
    }

    @Operation(summary = "Chạy job ngay",
        description = "Không phải đợi tới lượt định kỳ. 409 khi đang có lần chạy khác dở dang.")
    @PostMapping("/run")
    public ResponseEntity<CrawlRunResponse> runNow() {
        SocialMetricsUpdateJob instance = job.getIfAvailable();
        if (instance == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        CrawlRun run = instance.runOnce();
        if (run == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.ok(CrawlRunResponse.of(run));
    }
}
