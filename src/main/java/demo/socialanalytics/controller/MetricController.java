package demo.socialanalytics.controller;

import demo.socialanalytics.dto.request.MetricRequest;
import demo.socialanalytics.dto.request.PageQuery;
import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.dto.response.MetricResponse;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Metrics", description = "Lịch sử chỉ số tương tác (likes, shares, comments, followers)")
@RestController
@RequestMapping("/metrics")
public class MetricController {
    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @Operation(summary = "Lịch sử đo của một bài viết", description = "Mới nhất trước, phân trang ở DB")
    @GetMapping
    public ResponseEntity<PageResponse<MetricResponse>> listByPost(
        @RequestParam Long postId,
        @Valid PageQuery pageQuery
    ) {
        return ResponseEntity.ok(
            metricService.listByPost(postId, pageQuery.pageOrDefault(), pageQuery.limitOrDefault()));
    }

    @Operation(summary = "Chuỗi thời gian cho biểu đồ",
        description = "Sắp tăng dần theo thời điểm đo; bỏ trống from/to thì lấy 30 ngày gần nhất")
    @GetMapping("/series")
    public ResponseEntity<ListResponse<MetricResponse>> timeSeries(
        @RequestParam Long postId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(metricService.timeSeries(postId, from, to));
    }

    @Operation(summary = "Chi tiết một lần đo")
    @GetMapping("/{id}")
    public ResponseEntity<MetricResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(metricService.getById(id));
    }

    @Operation(summary = "Ghi nhận một lần đo", description = "Background job crawl sẽ gọi chính endpoint này")
    @PostMapping
    public ResponseEntity<MetricResponse> record(@Valid @RequestBody MetricRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metricService.record(request));
    }

    @Operation(summary = "Xoá một lần đo")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        metricService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
