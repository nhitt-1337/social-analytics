package demo.socialanalytics.entity;

// Kết quả một lần chạy job.
// Tách PARTIAL riêng khỏi SUCCESS/FAILED vì crawl vài chục tài khoản thì chuyện một hai tài khoản
// lỗi là bình thường — gọi cả lần chạy đó là "thất bại" sẽ che mất việc phần lớn đã chạy xong.
public enum CrawlStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED
}
