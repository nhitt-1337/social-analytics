package demo.socialanalytics.dto.excel;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.excel.ExcelColumn;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Một dòng trong file Excel import bài viết.
//
// Dùng class thường (không phải record) vì ExcelMapper ghi giá trị vào field bằng Reflection,
// mà field của record là final nên không set được.
//
// Tiêu đề cột để nguyên tên tiếng Anh trùng với field của API JSON: người dùng tự gõ lại
// tiêu đề cũng không vướng dấu tiếng Việt.
@Getter
@Setter
public class PostImportRow {

    @ExcelColumn(header = "platform", order = 1, required = true)
    private Platform platform;

    @ExcelColumn(header = "externalId", order = 2, required = true)
    private String externalId;

    @ExcelColumn(header = "content", order = 3)
    private String content;

    @ExcelColumn(header = "url", order = 4)
    private String url;

    @ExcelColumn(header = "postedAt", order = 5)
    private LocalDateTime postedAt;
}
