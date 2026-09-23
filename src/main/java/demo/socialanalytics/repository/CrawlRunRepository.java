package demo.socialanalytics.repository;

import demo.socialanalytics.entity.CrawlRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {
    Optional<CrawlRun> findFirstByOrderByStartedAtDescIdDesc();

    List<CrawlRun> findTop10ByOrderByStartedAtDescIdDesc();
}
