package demo.socialanalytics.service;

import demo.socialanalytics.dto.response.ImportResultResponse;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.excel.ExcelMapper;
import demo.socialanalytics.excel.ExcelParseException;
import demo.socialanalytics.messaging.ImportCompletedEvent;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.exception.ResourceNotFoundException;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.UserRepository;
import demo.socialanalytics.repository.projection.PostIdentity;
import demo.socialanalytics.support.ExcelTestFiles;
import demo.socialanalytics.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

// Unit test cho luồng import.
@ExtendWith(MockitoExtension.class)
class PostImportServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    PostImportService importService;
    User owner;

    @BeforeEach
    void setUp() {
        importService = new PostImportService(
            postRepository, userRepository, new ExcelMapper(), eventPublisher);
        owner = TestEntities.user(1L, "admin@example.com");
    }

    private MultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "posts.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private void ownerExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    }

    private void noExistingPosts() {
        when(postRepository.findIdentitiesByExternalIdIn(anyCollection())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Post> capturedSaved() {
        ArgumentCaptor<Iterable<Post>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(postRepository).saveAll(captor.capture());
        List<Post> saved = new java.util.ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    @Test
    void luuCacDongHopLeVaGanChungMotChuSoHuu() {
        ownerExists();
        noExistingPosts();
        LocalDateTime postedAt = LocalDateTime.of(2026, 3, 1, 9, 0);
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Bài 1", "https://fb.com/1", postedAt),
            List.of("twitter", "tw-002", "Bài 2", "https://x.com/2", postedAt)));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.errors()).isEmpty();

        List<Post> saved = capturedSaved();
        assertThat(saved).extracting(Post::getExternalId).containsExactly("fb-001", "tw-002");
        assertThat(saved).extracting(Post::getPlatform)
            .containsExactly(Platform.FACEBOOK, Platform.TWITTER);
        assertThat(saved).allSatisfy(post -> assertThat(post.getUser()).isSameAs(owner));
        assertThat(saved.get(0).getPostedAt()).isEqualTo(postedAt);
    }

    // Bài đã có trong DB thì bỏ qua, KHÔNG làm hỏng cả lần import.
    @Test
    void boQuaBaiDaCoTrongDatabase() {
        ownerExists();
        when(postRepository.findIdentitiesByExternalIdIn(anyCollection()))
            .thenReturn(List.of(new PostIdentity(Platform.FACEBOOK, "fb-001")));
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Đã có", "", ""),
            List.of("facebook", "fb-002", "Bài mới", "", "")));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(2);
            assertThat(error.message()).contains("fb-001").contains("đã tồn tại");
        });
        assertThat(capturedSaved()).extracting(Post::getExternalId).containsExactly("fb-002");
    }

    // Cùng một bài xuất hiện hai lần TRONG CHÍNH FILE cũng phải bị chặn, nếu không saveAll sẽ đụng
    @Test
    void boQuaDongTrungLapBenTrongCungMotFile() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Lần 1", "", ""),
            List.of("facebook", "fb-001", "Lần 2", "", "")));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(capturedSaved()).hasSize(1);
    }

    // Cùng externalId nhưng khác nền tảng là HAI bài khác nhau -> đều được nhận.
    @Test
    void cungExternalIdKhacNenTangThiKhongCoiLaTrung() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "post-1", "Bản FB", "", ""),
            List.of("twitter", "post-1", "Bản TW", "", "")));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(capturedSaved()).hasSize(2);
    }

    // Dòng sai định dạng bị loại nhưng các dòng còn lại vẫn vào DB.
    @Test
    void dongSaiDinhDangBiLoaiNhungDongConLaiVanDuocLuu() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Hợp lệ", "", ""),
            List.of("instagram", "ig-001", "Nền tảng lạ", "", ""),
            Arrays.asList(null, "fb-003", "Thiếu nền tảng", "", "")));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.errors()).extracting(ImportResultResponse.RowErrorResponse::rowNumber)
            .containsExactly(3, 4);
        assertThat(capturedSaved()).extracting(Post::getExternalId).containsExactly("fb-001");
    }

    // Dòng trùng nằm SAU một dòng lỗi phải được báo đúng số dòng trong file.
    @Test
    void dungSoDongKhiCoDongLoi() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-101", "Dòng 2 - hợp lệ", "", ""),
            List.of("twitter", "tw-202", "Dòng 3 - hợp lệ", "", ""),
            List.of("instagram", "ig-303", "Dòng 4 - nền tảng lạ", "", ""),
            Arrays.asList(null, "fb-404", "Dòng 5 - thiếu nền tảng", "", ""),
            List.of("facebook", "fb-101", "Dòng 6 - trùng dòng 2", "", "")));

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(3);
        assertThat(result.errors())
            .extracting(ImportResultResponse.RowErrorResponse::rowNumber)
            .containsExactlyInAnyOrder(4, 5, 6);
        assertThat(result.errors())
            .filteredOn(error -> error.message().contains("đã tồn tại"))
            .singleElement()
            .satisfies(error -> assertThat(error.rowNumber()).isEqualTo(6));
    }

    @Test
    void chiHoiDatabaseMotLan() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "", "", ""),
            List.of("facebook", "fb-002", "", "", ""),
            List.of("facebook", "fb-003", "", "", "")));

        importService.importPosts(upload(file), 1L);

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(postRepository, times(1)).findIdentitiesByExternalIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("fb-001", "fb-002", "fb-003");
        // Không được rơi về kiểu hỏi từng dòng.
        verify(postRepository, never()).existsByPlatformAndExternalId(any(), any());
        verify(postRepository, times(1)).saveAll(any());
    }

    @Test
    void fileChiCoTieuDeThiKhongLuuGiCa() {
        ownerExists();
        byte[] file = ExcelTestFiles.postsFile(List.of());

        ImportResultResponse result = importService.importPosts(upload(file), 1L);

        assertThat(result.totalRows()).isZero();
        assertThat(result.imported()).isZero();
        assertThat(capturedSaved()).isEmpty();
        verify(postRepository, never()).findIdentitiesByExternalIdIn(anyCollection());
    }

    @Test
    void thieuCotBatBuocThiTuChoiCaFile() {
        ownerExists();
        byte[] file = ExcelTestFiles.file(
            List.of("content", "url"),
            List.of(List.of("Không có platform", "https://example.com")));

        assertThatThrownBy(() -> importService.importPosts(upload(file), 1L))
            .isInstanceOf(ExcelParseException.class)
            .hasMessageContaining("platform");

        verify(postRepository, never()).saveAll(any());
    }

    @Test
    void nguoiDungKhongTonTai() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importService.importPosts(upload(ExcelTestFiles.postsFile(List.of())), 404L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("người dùng");

        verifyNoInteractions(postRepository);
    }

    // Phát sự kiện với ĐÚNG số liệu tóm tắt; ImportCompletedProducer mới là nơi đẩy lên hàng đợi.
    @Test
    void phatSuKienImportCompleted() {
        ownerExists();
        noExistingPosts();
        byte[] file = ExcelTestFiles.postsFile(List.of(
            List.of("facebook", "fb-001", "Hợp lệ", "", ""),
            List.of("instagram", "ig-001", "Nền tảng lạ", "", "")));

        importService.importPosts(upload(file), 1L);

        ArgumentCaptor<ImportCompletedEvent> captor = ArgumentCaptor.forClass(ImportCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var message = captor.getValue().message();
        assertThat(message.userId()).isEqualTo(1L);
        assertThat(message.totalRows()).isEqualTo(2);
        assertThat(message.imported()).isEqualTo(1);
        assertThat(message.skipped()).isEqualTo(1);
        assertThat(message.completedAt()).isNotNull();
    }

    // Import hỏng ở giữa chừng thì KHÔNG được báo "import xong".
    @Test
    void khongPhatSuKienKhiFileBiTuChoi() {
        ownerExists();
        byte[] file = ExcelTestFiles.file(
            List.of("content", "url"),
            List.of(List.of("Thiếu platform", "https://example.com")));

        assertThatThrownBy(() -> importService.importPosts(upload(file), 1L))
            .isInstanceOf(ExcelParseException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void tuChoiFileRong() {
        MultipartFile empty = new MockMultipartFile("file", "posts.xlsx", null, new byte[0]);

        assertThatThrownBy(() -> importService.importPosts(empty, 1L))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining("Chưa chọn file");

        verifyNoInteractions(userRepository, postRepository, eventPublisher);
    }

    @Test
    void tuChoiFileKhongPhaiDuoiExcel() {
        MultipartFile csv = new MockMultipartFile("file", "posts.csv", "text/csv",
            "platform,externalId".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> importService.importPosts(csv, 1L))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining(".xlsx");

        verifyNoInteractions(userRepository, postRepository, eventPublisher);
    }

    // Đuôi .xlsx nhưng nội dung không phải workbook.
    @Test
    void tuChoiFileDungDuoiNhungNoiDungKhongPhaiExcel() {
        ownerExists();
        MultipartFile fake = new MockMultipartFile("file", "posts.xlsx", null,
            "chỉ là văn bản thường".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> importService.importPosts(fake, 1L))
            .isInstanceOf(ExcelParseException.class)
            .hasMessageContaining("không phải file Excel hợp lệ");
    }
}
