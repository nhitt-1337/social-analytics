package demo.socialanalytics.controller;

import demo.socialanalytics.dto.response.DeadLetterResponse;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.dto.response.PlatformSummaryResponse;
import demo.socialanalytics.repository.DeadLetterRepository;
import demo.socialanalytics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Statistics", description = "Thống kê tổng hợp và hàng đợi thư chết")
@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;
    private final DeadLetterRepository deadLetterRepository;

    public StatisticsController(
        StatisticsService statisticsService, DeadLetterRepository deadLetterRepository) {
        this.statisticsService = statisticsService;
        this.deadLetterRepository = deadLetterRepository;
    }

    @Operation(summary = "Thống kê tổng hợp theo nền tảng",
        description = "Do listener IMPORT_COMPLETED tính lại sau mỗi lần import Excel")
    @GetMapping
    public ResponseEntity<ListResponse<PlatformSummaryResponse>> summaries() {
        return ResponseEntity.ok(ListResponse.of(
            statisticsService.current().stream().map(PlatformSummaryResponse::of).toList()));
    }

    @Operation(summary = "Message đã vào hàng đợi thư chết",
        description = "Những message thử lại hết số lần cho phép mà vẫn hỏng")
    @GetMapping("/dead-letters")
    public ResponseEntity<ListResponse<DeadLetterResponse>> deadLetters() {
        return ResponseEntity.ok(ListResponse.of(
            deadLetterRepository.findTop20ByOrderByReceivedAtDescIdDesc()
                .stream().map(DeadLetterResponse::of).toList()));
    }
}
