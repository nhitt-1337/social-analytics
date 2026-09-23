package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Credential của từng nhà cung cấp Social Login. Tiền tố: social.login.*
@ConfigurationProperties(prefix = "social.login")
public record SocialLoginProperties(
    @DefaultValue Credentials facebook,
    @DefaultValue Credentials x
) {

    public record Credentials(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,

        // Quyền xin từ nhà cung cấp.
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
