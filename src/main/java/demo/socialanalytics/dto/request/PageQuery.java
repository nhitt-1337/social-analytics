package demo.socialanalytics.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Tham số phân trang dùng chung: ?page=&limit= (page đếm từ 1, limit có trần).
public record PageQuery(
    @Min(value = 1, message = "page phải lớn hơn hoặc bằng 1")
    Integer page,

    @Min(value = 1, message = "limit phải lớn hơn hoặc bằng 1")
    @Max(value = MAX_LIMIT, message = "limit không được vượt quá " + MAX_LIMIT)
    Integer limit
) {
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;

    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
