package demo.socialanalytics.controller;

import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.service.ChartDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chart", description = "Dữ liệu tổng hợp cho biểu đồ")
@RestController
public class ChartController {

    private final ChartDataService chartDataService;

    public ChartController(ChartDataService chartDataService) {
        this.chartDataService = chartDataService;
    }

    @Operation(
        summary = "Dữ liệu biểu đồ",
        description = "Tổng tương tác theo từng ngày, lấy lần đo cuối của mỗi bài trong mỗi ngày. "
            + "Lọc theo platform (facebook|twitter); days mặc định 7, tối đa 90.")
    @GetMapping("/chart-data")
    public ResponseEntity<ChartDataResponse> chartData(
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(chartDataService.chartData(platform, days));
    }
}
