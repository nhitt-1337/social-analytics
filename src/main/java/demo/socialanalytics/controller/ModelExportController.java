package demo.socialanalytics.controller;

import demo.socialanalytics.dto.response.ListResponse;
import demo.socialanalytics.service.ModelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Một endpoint xuất Excel cho nhiều loại model. Thêm model mới chỉ cần đăng ký ở
// ModelExportService, không phải thêm endpoint.
@Tag(name = "Export", description = "Xuất model bất kỳ ra Excel bằng Reflection")
@RestController
@RequestMapping("/export")
public class ModelExportController {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ModelExportService modelExportService;

    public ModelExportController(ModelExportService modelExportService) {
        this.modelExportService = modelExportService;
    }

    @Operation(summary = "Danh sách model xuất được, kèm các cột của từng model")
    @GetMapping("/models")
    public ResponseEntity<ListResponse<ModelInfo>> models() {
        List<ModelInfo> models = modelExportService.availableModels().stream()
            .map(name -> new ModelInfo(name, modelExportService.headersOf(name)))
            .toList();
        return ResponseEntity.ok(ListResponse.of(models));
    }

    public record ModelInfo(String model, List<String> columns) {
    }

    @Operation(summary = "Xuất một model ra Excel",
        description = "Cột được suy ra từ chính lớp model bằng Reflection")
    @GetMapping("/{model}")
    public ResponseEntity<Resource> export(@PathVariable String model) {
        byte[] workbook = modelExportService.export(model);

        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(modelExportService.fileNameOf(model), StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentLength(workbook.length)
            .body(new ByteArrayResource(workbook));
    }
}
