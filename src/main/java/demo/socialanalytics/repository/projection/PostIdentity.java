package demo.socialanalytics.repository.projection;

import demo.socialanalytics.entity.Platform;

// Cặp khoá định danh bài viết. Dùng để lấy về danh sách bài ĐÃ CÓ bằng một truy vấn duy nhất
// khi import, thay vì hỏi DB từng dòng một.
public record PostIdentity(Platform platform, String externalId) {
}
