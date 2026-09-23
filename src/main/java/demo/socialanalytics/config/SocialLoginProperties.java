package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Credential của từng nhà cung cấp Social Login. Tiền tố: social.login.*
//
// Không dùng thẳng spring.security.oauth2.client.registration.* vì cần biết provider nào THẬT SỰ
// đã được cấu hình để chỉ đăng ký những cái đó (xem SocialLoginClientRegistrations).
@ConfigurationProperties(prefix = "social.login")
public record SocialLoginProperties(
    @DefaultValue Credentials facebook,
    @DefaultValue Credentials x
) {

    public record Credentials(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,

        // Quyền xin từ nhà cung cấp.
        //
        // Để cấu hình được vì mỗi app khai quyền khác nhau: Facebook chỉ cấp sẵn
        // `public_profile`, còn `email` phải bật riêng trong Use cases. Xin một quyền app
        // chưa có thì Facebook chặn ngay ở màn hình đăng nhập ("Invalid Scopes: email"),
        // nên phải bỏ được quyền đó mà không cần sửa code.
        @DefaultValue({"public_profile", "email"}) java.util.List<String> scopes
    ) {
        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }

    public boolean anyConfigured() {
        return facebook.isConfigured() || x.isConfigured();
    }
}
