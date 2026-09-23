package demo.socialanalytics.service;

import demo.socialanalytics.dto.response.*;
import demo.socialanalytics.excel.ModelExporter;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.repository.DeadLetterRepository;
import demo.socialanalytics.repository.CrawlRunRepository;
import demo.socialanalytics.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

// Xuất Excel theo TÊN MODEL, dùng chung một đường đi cho mọi loại dữ liệu.
//
// Thêm một model mới = thêm một dòng vào bảng đăng ký dưới đây. Không phải viết thêm endpoint,
// không phải viết thêm DTO gắn @ExcelColumn — ModelExporter tự suy ra cột bằng Reflection.
@Service
@Transactional(readOnly = true)
public class ModelExportService {

    private static final int MAX_ROWS = 10_000;

    private final ModelExporter modelExporter;
    private final Map<String, ModelSource<?>> registry = new LinkedHashMap<>();

    // Giữ kiểu và nguồn dữ liệu đi cùng nhau để không bị lệch.
    private record ModelSource<T>(Class<T> type, String sheetName, Supplier<List<T>> loader) {
    }

    public ModelExportService(
        ModelExporter modelExporter,
        PostRepository postRepository,
        CrawlRunRepository crawlRunRepository,
        DeadLetterRepository deadLetterRepository,
        StatisticsService statisticsService
    ) {
        this.modelExporter = modelExporter;

        register("posts", PostSummaryRow.class, "Bai viet", () ->
            postRepository.findAll(PageRequest.of(0, MAX_ROWS,
                    Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream()
                .map(post -> new PostSummaryRow(
                    post.getId(), post.getPlatform().getSlug(), post.getExternalId(),
                    post.getContent(), post.getUrl(), post.getPostedAt(), post.getCreatedAt()))
                .toList());

        register("crawl-runs", CrawlRunResponse.class, "Lan chay job", () ->
            crawlRunRepository.findTop10ByOrderByStartedAtDescIdDesc()
                .stream().map(CrawlRunResponse::of).toList());

        register("statistics", PlatformSummaryResponse.class, "Thong ke", () ->
            statisticsService.current().stream().map(PlatformSummaryResponse::of).toList());

        register("dead-letters", DeadLetterResponse.class, "Thu chet", () ->
            deadLetterRepository.findTop20ByOrderByReceivedAtDescIdDesc()
                .stream().map(DeadLetterResponse::of).toList());
    }

    // Dòng cho model "posts". Là record thường, KHÔNG gắn @ExcelColumn — cột do Reflection suy ra.
    public record PostSummaryRow(
        Long id,
        String platform,
        String externalId,
        String content,
        String url,
        java.time.LocalDateTime postedAt,
        java.time.LocalDateTime createdAt
    ) {
    }

    private <T> void register(String name, Class<T> type, String sheetName, Supplier<List<T>> loader) {
        registry.put(name, new ModelSource<>(type, sheetName, loader));
    }

    public List<String> availableModels() {
        return List.copyOf(registry.keySet());
    }

    public List<String> headersOf(String model) {
        return modelExporter.headers(source(model).type());
    }

    @SuppressWarnings("unchecked")
    public <T> byte[] export(String model) {
        ModelSource<T> source = (ModelSource<T>) source(model);
        return modelExporter.export(source.loader().get(), source.type(), source.sheetName());
    }

    public String fileNameOf(String model) {
        return model + "-" + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
    }

    private ModelSource<?> source(String model) {
        ModelSource<?> source = model == null
            ? null
            : registry.get(model.trim().toLowerCase(Locale.ROOT));
        if (source == null) {
            throw new InvalidRequestParameterException(
                "Không có model '" + model + "'. Cho phép: " + availableModels());
        }
        return source;
    }
}
