package demo.socialanalytics.service;

public record AccountCrawlResult(long userId, int totalPosts, int succeeded, int failed) {
    public static AccountCrawlResult empty(long userId) {
        return new AccountCrawlResult(userId, 0, 0, 0);
    }

    public boolean hasFailure() {
        return failed > 0;
    }
}
