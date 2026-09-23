package demo.socialanalytics.messaging;

import java.time.LocalDateTime;

// Nội dung message IMPORT_COMPLETED.
//
// Chỉ mang SỐ LIỆU TÓM TẮT và id người dùng, không mang cả danh sách bài viết: message nên đủ
// nhỏ để hàng đợi không phình ra, còn bên nhận cần chi tiết thì tự đọc DB.
//
// Là record bất biến và được chuyển thành JSON, nên thêm trường mới vẫn đọc được message cũ.
public record ImportCompletedMessage(
    Long userId,
    int totalRows,
    int imported,
    int skipped,
    LocalDateTime completedAt
) {
}
