package demo.socialanalytics.repository;

import demo.socialanalytics.entity.AuthProvider;
import demo.socialanalytics.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Dùng cho Social Login ở bước sau: tìm tài khoản đã liên kết với id bên nền tảng.
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
