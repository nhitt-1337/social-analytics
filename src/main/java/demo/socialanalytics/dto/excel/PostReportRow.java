package demo.socialanalytics.dto.excel;

import demo.socialanalytics.excel.ExcelColumn;

import java.time.LocalDateTime;

// Một dòng trong file báo cáo xuất ra. Chỉ ghi nên dùng record được:
// ExcelMapper chỉ đọc field (Field.get) chứ không set.
//
// Tiêu đề tiếng Việt vì file này người dùng mở ra đọc trực tiếp.
// Các cột chỉ số lấy từ lần đo GẦN NHẤT của bài; bài chưa crawl lần nào thì để trống.
public record PostReportRow(

    @ExcelColumn(header = "ID", order = 1)
    Long id,

    @ExcelColumn(header = "Nền tảng", order = 2)
    String platform,

    @ExcelColumn(header = "Mã bài viết", order = 3)
    String externalId,

    @ExcelColumn(header = "Nội dung", order = 4)
    String content,

    @ExcelColumn(header = "Đường dẫn", order = 5)
    String url,

    @ExcelColumn(header = "Đăng lúc", order = 6)
    LocalDateTime postedAt,

    @ExcelColumn(header = "Người quản lý", order = 7)
    String ownerName,

    @ExcelColumn(header = "Email", order = 8)
    String ownerEmail,

    @ExcelColumn(header = "Lượt thích", order = 9)
    Integer likes,

    @ExcelColumn(header = "Lượt chia sẻ", order = 10)
    Integer shares,

    @ExcelColumn(header = "Bình luận", order = 11)
    Integer comments,

    @ExcelColumn(header = "Người theo dõi", order = 12)
    Integer followers,

    @ExcelColumn(header = "Đo lúc", order = 13)
    LocalDateTime collectedAt
) {
}
