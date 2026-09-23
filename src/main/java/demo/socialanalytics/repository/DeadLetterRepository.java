package demo.socialanalytics.repository;

import demo.socialanalytics.entity.DeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {

    List<DeadLetter> findTop20ByOrderByReceivedAtDescIdDesc();

    long countBySourceQueue(String sourceQueue);
}
