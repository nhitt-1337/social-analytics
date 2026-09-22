package demo.socialanalytics.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Bao ngoài kết quả phân trang: { data, total, page, limit }. page là số trang đếm từ 1.
public record PageResponse<T>(
    List<T> data,
    long total,
    int page,
    int limit
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber() + 1,
            page.getSize()
        );
    }
}
