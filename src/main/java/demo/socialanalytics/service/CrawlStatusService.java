package demo.socialanalytics.service;

import demo.socialanalytics.dto.response.CrawlRunResponse;
import demo.socialanalytics.repository.CrawlRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CrawlStatusService {

    private final CrawlRunRepository crawlRunRepository;

    public CrawlStatusService(CrawlRunRepository crawlRunRepository) {
        this.crawlRunRepository = crawlRunRepository;
    }

    // Rỗng khi job chưa chạy lần nào. Dashboard hiển thị "chưa cập nhật lần nào" thay vì
    // bịa ra một mốc thời gian.
    public Optional<CrawlRunResponse> lastRun() {
        return crawlRunRepository.findFirstByOrderByStartedAtDescIdDesc().map(CrawlRunResponse::of);
    }

    public List<CrawlRunResponse> recentRuns() {
        return crawlRunRepository.findTop10ByOrderByStartedAtDescIdDesc()
            .stream().map(CrawlRunResponse::of).toList();
    }
}
