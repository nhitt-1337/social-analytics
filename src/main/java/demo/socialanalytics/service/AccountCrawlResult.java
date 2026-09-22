package demo.socialanalytics.service;

// Kết quả crawl của MỘT tài khoản. Trả về kiểu này thay vì ném ngoại lệ, vì một tài khoản
// hỏng không được phép làm đổ cả lần chạy.
public record AccountCrawlResult(long userId, int totalPosts, int succeeded, int failed) {

    public static AccountCrawlResult empty(long userId) {
        return new AccountCrawlResult(userId, 0, 0, 0);
    }

    public boolean hasFailure() {
        return failed > 0;
    }
}
