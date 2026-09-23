package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;
import demo.socialanalytics.entity.Role;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Chạy sau khi đổi code lấy token xong: gọi user-info của nhà cung cấp rồi lưu/cập nhật User.
@Service
public class SocialLoginUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    // Khoá thuộc tính dùng làm tên định danh của principal, vd "facebook:123456".
    public static final String PRINCIPAL_ATTRIBUTE = "principal";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;

    public SocialLoginUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User raw = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        SocialUserAttributes social;
        try {
            social = SocialUserAttributes.of(registrationId, raw.getAttributes());
        } catch (IllegalArgumentException exception) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", exception.getMessage(), null), exception);
        }
        if (social.providerId() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info",
                "Nhà cung cấp không trả về id người dùng", null));
        }

        User user = upsert(social);

        // Chỉ đưa vào phiên những thuộc tính mình thật sự dùng
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(PRINCIPAL_ATTRIBUTE, social.principalName());
        attributes.put("userId", user.getId());
        attributes.put("provider", social.provider().getSlug());
        attributes.put("fullName", user.getFullName());
        attributes.put("email", user.getEmail());
        attributes.put("avatarUrl", user.getAvatarUrl());

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
            attributes,
            PRINCIPAL_ATTRIBUTE);
    }

    // Đăng nhập lần đầu thì tạo tài khoản, lần sau thì cập nhật thông tin hồ sơ.
    private User upsert(SocialUserAttributes social) {
        User user = userRepository
            .findByProviderAndProviderId(social.provider(), social.providerId())
            .orElseGet(() -> linkOrCreate(social));

        user.setFullName(social.fullName() != null ? social.fullName() : user.getFullName());
        if (social.email() != null) {
            user.setEmail(social.email());
        }
        if (social.avatarUrl() != null) {
            user.setAvatarUrl(social.avatarUrl());
        }
        return userRepository.save(user);
    }

    // Có tài khoản LOCAL trùng email thì gắn vào đó, vì cột email là unique
    private User linkOrCreate(SocialUserAttributes social) {
        Optional<User> byEmail = social.email() == null
            ? Optional.empty()
            : userRepository.findByEmail(social.email());

        User user = byEmail
            .filter(existing -> existing.getProvider() == AuthProvider.LOCAL
                && existing.getProviderId() == null)
            .orElseGet(User::new);

        user.setProvider(social.provider());
        user.setProviderId(social.providerId());
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
        return user;
    }
}
