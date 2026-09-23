package demo.socialanalytics.repository;

import demo.socialanalytics.entity.AuthorizedClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
// Hậu tố Jpa để không trùng tên bean với authorizedClientRepository của Spring Boot
public interface AuthorizedClientJpaRepository extends JpaRepository<AuthorizedClient, Long> {
    Optional<AuthorizedClient> findByRegistrationIdAndPrincipalName(
        String registrationId, String principalName);

    void deleteByRegistrationIdAndPrincipalName(String registrationId, String principalName);
}
