package demo.socialanalytics.service;

import demo.socialanalytics.dto.excel.PostImportRow;
import demo.socialanalytics.dto.response.ImportResultResponse;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.excel.ExcelMapper;
import demo.socialanalytics.excel.ExcelParseException;
import demo.socialanalytics.excel.ExcelReadResult;
import demo.socialanalytics.excel.ExcelRow;
import demo.socialanalytics.messaging.ImportCompletedEvent;
import demo.socialanalytics.messaging.ImportCompletedMessage;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.UserRepository;
import demo.socialanalytics.repository.projection.PostIdentity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Nhập danh sách bài viết từ file Excel.
@Service
public class PostImportService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".xlsx", ".xls");

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ExcelMapper excelMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PostImportService(
        PostRepository postRepository,
        UserRepository userRepository,
        ExcelMapper excelMapper,
        ApplicationEventPublisher eventPublisher
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.excelMapper = excelMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ImportResultResponse importPosts(MultipartFile file, Long userId) {
        requireExcelFile(file);

        // Chủ sở hữu của toàn bộ bài trong file.
        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("người dùng"));

        ExcelReadResult<PostImportRow> result = read(file);

        List<ImportResultResponse.RowErrorResponse> errors = new ArrayList<>(
            result.errors().stream()
                .map(error -> new ImportResultResponse.RowErrorResponse(error.rowNumber(), error.message()))
                .toList());

        List<ExcelRow<PostImportRow>> rows = result.rows();
        Set<String> existing = loadExistingKeys(result.values());
        List<Post> toSave = new ArrayList<>();

        for (ExcelRow<PostImportRow> entry : rows) {
            PostImportRow row = entry.value();
            // Lấy số dòng do ExcelMapper ghi lại
            int rowNumber = entry.rowNumber();
            String key = key(row);

            // existing chứa cả bài đã có trong DB lẫn bài vừa gặp ở dòng trên trong cùng file
            if (!existing.add(key)) {
                errors.add(new ImportResultResponse.RowErrorResponse(rowNumber,
                    "bài viết " + row.getExternalId() + " trên " + row.getPlatform().getSlug()
                        + " đã tồn tại, bỏ qua"));
                continue;
            }
            toSave.add(toEntity(row, owner));
        }

        postRepository.saveAll(toSave);

        ImportResultResponse response = new ImportResultResponse(
            result.totalRows(),
            toSave.size(),
            result.totalRows() - toSave.size(),
            List.copyOf(errors));

        // Phát sự kiện nội bộ; ImportCompletedProducer mới là nơi đẩy lên hàng đợi, và chỉ đẩy
        eventPublisher.publishEvent(new ImportCompletedEvent(new ImportCompletedMessage(
            userId, response.totalRows(), response.imported(), response.skipped(),
            LocalDateTime.now())));

        return response;
    }

    private ExcelReadResult<PostImportRow> read(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return excelMapper.read(input, PostImportRow.class);
        } catch (IOException exception) {
            throw new ExcelParseException("Không đọc được file tải lên: " + exception.getMessage(), exception);
        }
    }

    // Một truy vấn duy nhất cho cả file.
    private Set<String> loadExistingKeys(List<PostImportRow> rows) {
        if (rows.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> externalIds = rows.stream().map(PostImportRow::getExternalId).collect(java.util.stream.Collectors.toSet());
        Set<String> keys = new HashSet<>();
        for (PostIdentity identity : postRepository.findIdentitiesByExternalIdIn(externalIds)) {
            keys.add(identity.platform().name() + ":" + identity.externalId());
        }
        return keys;
    }

    private String key(PostImportRow row) {
        return row.getPlatform().name() + ":" + row.getExternalId();
    }

    private Post toEntity(PostImportRow row, User owner) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(row.getPlatform());
        post.setExternalId(row.getExternalId());
        post.setContent(row.getContent());
        post.setUrl(row.getUrl());
        post.setPostedAt(row.getPostedAt());
        return post;
    }

    private void requireExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestParameterException("Chưa chọn file Excel để tải lên");
        }
        String name = file.getOriginalFilename();
        if (name == null || ALLOWED_EXTENSIONS.stream()
            .noneMatch(extension -> name.toLowerCase(Locale.ROOT).endsWith(extension))) {
            throw new InvalidRequestParameterException(
                "Chỉ nhận file Excel có đuôi " + String.join(" hoặc ", ALLOWED_EXTENSIONS));
        }
    }
}
