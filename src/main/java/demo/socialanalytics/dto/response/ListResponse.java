package demo.socialanalytics.dto.response;

import java.util.List;

// Danh sách không phân trang, vẫn bọc object để không trả JSON array trần.
public record ListResponse<T>(List<T> data) {
    public static <T> ListResponse<T> of(List<T> data) {
        return new ListResponse<>(data);
    }
}
