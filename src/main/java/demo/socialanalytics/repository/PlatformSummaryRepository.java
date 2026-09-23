package demo.socialanalytics.repository;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.PlatformSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformSummaryRepository extends JpaRepository<PlatformSummary, Long> {
    Optional<PlatformSummary> findByPlatform(Platform platform);

    List<PlatformSummary> findAllByOrderByPlatformAsc();
}
