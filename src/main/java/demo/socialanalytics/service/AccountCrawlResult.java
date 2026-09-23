package demo.socialanalytics.service;

// Kết quả crawl của MỘT tài khoản.
public record AccountCrawlResult(long userId, int totalPosts, int succeeded, int failed) {

    public static AccountCrawlResult empty(long userId) {
        return new AccountCrawlResult(userId, 0, 0, 0);
    }

    public boolean hasFailure() {
        return failed > 0;
    }
}
