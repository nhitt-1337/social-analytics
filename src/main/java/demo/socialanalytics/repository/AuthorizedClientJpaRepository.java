package demo.socialanalytics.repository;

import demo.socialanalytics.entity.AuthorizedClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
// Tên có hậu tố Jpa để không trùng tên bean với `authorizedClientRepository` mà Spring Boot
// tự tạo cho OAuth2 (kiểu OAuth2AuthorizedClientRepository) — trùng tên là context không khởi
// động được.
public interface AuthorizedClientJpaRepository extends JpaRepository<AuthorizedClient, Long> {

    Optional<AuthorizedClient> findByRegistrationIdAndPrincipalName(
        String registrationId, String principalName);

    void deleteByRegistrationIdAndPrincipalName(String registrationId, String principalName);
}
