package demo.socialanalytics.service;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.excel.ExcelMapper;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.support.ExcelTestFiles;
import demo.socialanalytics.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Unit test cho luồng export
@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock PostRepository postRepository;
    @Mock SocialMetricRepository metricRepository;

    ReportExportService exportService;
    User owner;

    @BeforeEach
    void setUp() {
        exportService = new ReportExportService(postRepository, metricRepository, new ExcelMapper());
        owner = TestEntities.user(1L, "admin@example.com");
    }

    private void noMetrics() {
        when(metricRepository.findByPostIdInOrderByCollectedAtDesc(anyCollection())).thenReturn(List.of());
    }

    @Test
    void xuatDongTieuDeKemSoLieuCuaLanDoGanNhat() {
        Post post = TestEntities.post(10L, owner, Platform.FACEBOOK, "fb-001");
        post.setPostedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        SocialMetric latest = TestEntities.metric(2L, post, 500, LocalDateTime.of(2026, 4, 2, 8, 0));

        when(postRepository.findForReport(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(List.of(post));
        when(metricRepository.findByPostIdInOrderByCollectedAtDesc(List.of(10L)))
            .thenReturn(List.of(latest));

        List<List<String>> sheet = ExcelTestFiles.readAll(exportService.exportReport(null, null, null));

        assertThat(sheet.get(0)).containsExactly(
            "ID", "Nền tảng", "Mã bài viết", "Nội dung", "Đường dẫn", "Đăng lúc",
            "Người quản lý", "Email", "Lượt thích", "Lượt chia sẻ", "Bình luận",
            "Người theo dõi", "Đo lúc");
        assertThat(sheet.get(1)).contains("10", "facebook", "fb-001", "admin@example.com", "500");
        assertThat(sheet).hasSize(2);
    }

    // Repository trả về đã sắp giảm dần theo thời điểm đo; service phải lấy bản ĐẦU TIÊN.
    @Test
    void chonDungLanDoGanNhatKhiMotBaiCoNhieuLanDo() {
        Post post = TestEntities.post(10L, owner, Platform.FACEBOOK, "fb-001");
        SocialMetric newer = TestEntities.metric(2L, post, 900, LocalDateTime.of(2026, 4, 2, 8, 0));
        SocialMetric older = TestEntities.metric(1L, post, 100, LocalDateTime.of(2026, 4, 1, 8, 0));

        when(postRepository.findForReport(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(post));
        when(metricRepository.findByPostIdInOrderByCollectedAtDesc(anyCollection()))
            .thenReturn(List.of(newer, older));

        List<List<String>> sheet = ExcelTestFiles.readAll(exportService.exportReport(null, null, null));

        assertThat(sheet.get(1).get(8)).isEqualTo("900");
    }

    // Bài chưa crawl lần nào -> ô chỉ số để TRỐNG, không ghi 0, tránh nhầm "chưa đo" với "đo được 0".
    @Test
    void baiChuaCoLanDoNaoThiDeTrongCacCotChiSo() {
        Post post = TestEntities.post(10L, owner, Platform.TWITTER, "tw-001");
        when(postRepository.findForReport(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(post));
        noMetrics();

        List<List<String>> sheet = ExcelTestFiles.readAll(exportService.exportReport(null, null, null));

        // Cột 8..12 là likes, shares, comments, followers, collectedAt.
        assertThat(sheet.get(1).subList(8, 13)).containsOnly("");
    }

    @Test
    void chuyenChuoiNenTangThanhEnumTruocKhiTruyVan() {
        when(postRepository.findForReport(eq(Platform.TWITTER), any(), any(), any(Pageable.class)))
            .thenReturn(List.of());

        exportService.exportReport("TWITTER", null, null);

        verify(postRepository).findForReport(eq(Platform.TWITTER), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void truyenThangKhoangThoiGianXuongRepository() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 2, 1, 0, 0);
        when(postRepository.findForReport(isNull(), eq(from), eq(to), any(Pageable.class)))
            .thenReturn(List.of());

        exportService.exportReport(null, from, to);

        verify(postRepository).findForReport(isNull(), eq(from), eq(to), any(Pageable.class));
    }

    @Test
    void chanSoDongToiDaMoiLanXuat() {
        when(postRepository.findForReport(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        exportService.exportReport(null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findForReport(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ReportExportService.MAX_EXPORT_ROWS);
    }

    @Test
    void khongCoBaiNaoThiVanXuatFileChiCoDongTieuDe() {
        when(postRepository.findForReport(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        List<List<String>> sheet = ExcelTestFiles.readAll(exportService.exportReport(null, null, null));

        assertThat(sheet).hasSize(1);
        assertThat(sheet.get(0)).first().isEqualTo("ID");
        // Không có bài nào thì khỏi đi hỏi bảng metrics.
        verifyNoInteractions(metricRepository);
    }

    // Chỉ MỘT truy vấn metrics cho toàn bộ báo cáo, không phải mỗi bài một truy vấn.
    @Test
    void layChiSoCuaMoiBai() {
        List<Post> posts = List.of(
            TestEntities.post(10L, owner, Platform.FACEBOOK, "fb-001"),
            TestEntities.post(11L, owner, Platform.TWITTER, "tw-001"),
            TestEntities.post(12L, owner, Platform.FACEBOOK, "fb-002"));
        when(postRepository.findForReport(any(), any(), any(), any(Pageable.class))).thenReturn(posts);
        noMetrics();

        exportService.exportReport(null, null, null);

        verify(metricRepository, times(1)).findByPostIdInOrderByCollectedAtDesc(List.of(10L, 11L, 12L));
        verify(metricRepository, never()).findFirstByPostIdOrderByCollectedAtDescIdDesc(anyLong());
    }

    @Test
    void nenTangKhongHopLe() {
        assertThatThrownBy(() -> exportService.exportReport("instagram", null, null))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining("instagram");

        verifyNoInteractions(postRepository);
    }

    @Test
    void fromSauToThiBaoLoi() {
        LocalDateTime from = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 1, 0, 0);

        assertThatThrownBy(() -> exportService.exportReport(null, from, to))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining("from phải trước to");

        verifyNoInteractions(postRepository);
    }

    @Test
    void tenFileCoDuoiXlsx() {
        assertThat(exportService.buildFileName())
            .startsWith("bao-cao-tuong-tac-")
            .endsWith(".xlsx");
    }
}
