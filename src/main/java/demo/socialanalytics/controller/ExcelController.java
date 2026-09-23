package demo.socialanalytics.controller;

import demo.socialanalytics.dto.response.ImportResultResponse;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.service.PostImportService;
import demo.socialanalytics.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

// Nhập/xuất Excel. Controller mỏng như các controller khác: không đụng POI, không truy vấn DB.
@Tag(name = "Excel", description = "Nhập bài viết từ Excel và xuất báo cáo tương tác")
@RestController
public class ExcelController {

    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PostImportService postImportService;
    private final ReportExportService reportExportService;

    public ExcelController(PostImportService postImportService, ReportExportService reportExportService) {
        this.postImportService = postImportService;
        this.reportExportService = reportExportService;
    }

    @Operation(
        summary = "Nhập bài viết từ file Excel",
        description = "File .xlsx với các cột: platform, externalId, content, url, postedAt. "
            + "Bỏ trống userId thì lấy người đang đăng nhập. "
            + "Dòng sai định dạng hoặc trùng bài đã có sẽ bị bỏ qua và liệt kê trong errors, "
            + "các dòng còn lại vẫn được lưu.")
    @PostMapping(value = "/import-posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultResponse> importPosts(
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) Long userId,
        @AuthenticationPrincipal OAuth2User principal
    ) {
        return ResponseEntity.ok(postImportService.importPosts(file, resolveOwner(userId, principal)));
    }

    // Bỏ trống userId thì lấy người đang đăng nhập, để form trên dashboard không phải hỏi id.
    private Long resolveOwner(Long userId, OAuth2User principal) {
        if (userId != null) {
            return userId;
        }
        Object fromSession = principal == null ? null : principal.getAttribute("userId");
        if (fromSession instanceof Number number) {
            return number.longValue();
        }
        throw new InvalidRequestParameterException(
            "Thiếu userId và không xác định được người dùng từ phiên đăng nhập");
    }

    @Operation(
        summary = "Xuất báo cáo tương tác ra Excel",
        description = "Mỗi dòng là một bài viết kèm số liệu của lần đo gần nhất. "
            + "Lọc theo platform (facebook|twitter) và khoảng thời gian đăng bài.")
    @GetMapping("/export-report")
    public ResponseEntity<Resource> exportReport(
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        byte[] workbook = reportExportService.exportReport(platform, from, to);

        // ContentDisposition tự lo phần mã hoá tên file (filename*) cho ký tự ngoài ASCII.
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(reportExportService.buildFileName(), StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentLength(workbook.length)
            .body(new ByteArrayResource(workbook));
    }
}
