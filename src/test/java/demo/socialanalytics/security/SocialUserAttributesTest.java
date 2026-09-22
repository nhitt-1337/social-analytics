package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Mỗi nhà cung cấp trả một kiểu JSON khác nhau; đây là chỗ kiểm phần đọc hiểu đó.
// Không cần Spring, không gọi mạng — chỉ là ánh xạ Map -> record.
class SocialUserAttributesTest {

    @Nested
    class Facebook {

        @Test
        void docDuocJsonPhangCuaFacebook() {
            var attributes = Map.<String, Object>of(
                "id", "1234567890",
                "name", "Nguyễn Văn A",
                "email", "a@example.com");

            var social = SocialUserAttributes.of("facebook", attributes);

            assertThat(social.provider()).isEqualTo(AuthProvider.FACEBOOK);
            assertThat(social.providerId()).isEqualTo("1234567890");
            assertThat(social.fullName()).isEqualTo("Nguyễn Văn A");
            assertThat(social.email()).isEqualTo("a@example.com");
            // Ảnh đại diện Facebook không nằm trong payload, phải tự dựng URL từ id.
            assertThat(social.avatarUrl()).isEqualTo(
                "https://graph.facebook.com/1234567890/picture?type=large");
        }

        // Người dùng có thể từ chối chia sẻ email dù đã xin scope.
        @Test
        void chapNhanFacebookKhongTraEmail() {
            var social = SocialUserAttributes.of("facebook",
                Map.of("id", "1", "name", "Không email"));

            assertThat(social.email()).isNull();
            assertThat(social.providerId()).isEqualTo("1");
        }
    }

    @Nested
    class X {

        // /2/users/me trả LỒNG trong "data" — lý do không dùng được DefaultOAuth2UserService
        // nguyên bản cho X.
        @Test
        void docDuocJsonLongTrongDataCuaX() {
            Map<String, Object> attributes = Map.of("data", Map.of(
                "id", "987654321",
                "name", "Trần Thị B",
                "username", "tranthib",
                "profile_image_url", "https://pbs.twimg.com/b.jpg"));

            var social = SocialUserAttributes.of("x", attributes);

            assertThat(social.provider()).isEqualTo(AuthProvider.TWITTER);
            assertThat(social.providerId()).isEqualTo("987654321");
            assertThat(social.fullName()).isEqualTo("Trần Thị B");
            assertThat(social.avatarUrl()).isEqualTo("https://pbs.twimg.com/b.jpg");
        }

        // X không trả email (cần quyền riêng) -> phải là null, không được bịa ra giá trị.
        @Test
        void xKhongCoEmail() {
            var social = SocialUserAttributes.of("x",
                Map.of("data", Map.of("id", "1", "username", "abc")));

            assertThat(social.email()).isNull();
        }

        // Tài khoản không đặt tên hiển thị thì lấy tạm username làm tên.
        @Test
        void thieuNameThiDungUsername() {
            var social = SocialUserAttributes.of("x",
                Map.of("data", Map.of("id", "1", "username", "abc")));

            assertThat(social.fullName()).isEqualTo("abc");
        }

        // Phòng khi X đổi sang trả phẳng: vẫn đọc được, không vỡ.
        @Test
        void vanDocDuocNeuKhongCoLopDataBocNgoai() {
            var social = SocialUserAttributes.of("x",
                Map.of("id", "1", "username", "abc", "name", "Tên"));

            assertThat(social.providerId()).isEqualTo("1");
            assertThat(social.fullName()).isEqualTo("Tên");
        }

        // "twitter" và "x" là cùng một nơi.
        @Test
        void chapNhanCaTenGoiTwitterLanX() {
            assertThat(AuthProvider.fromRegistrationId("twitter")).isEqualTo(AuthProvider.TWITTER);
            assertThat(AuthProvider.fromRegistrationId("X")).isEqualTo(AuthProvider.TWITTER);
        }
    }

    // Ghép cả nhà cung cấp vào tên định danh: id trùng nhau giữa hai nền tảng không được
    // coi là cùng một người.
    @Test
    void principalNameGomCaNhaCungCapVaId() {
        var facebook = SocialUserAttributes.of("facebook", Map.of("id", "100", "name", "A"));
        var x = SocialUserAttributes.of("x", Map.of("data", Map.of("id", "100", "username", "a")));

        assertThat(facebook.principalName()).isEqualTo("facebook:100");
        assertThat(x.principalName()).isEqualTo("twitter:100");
        assertThat(facebook.principalName()).isNotEqualTo(x.principalName());
    }

    @Test
    void oTrongCoiNhuKhongCoGiaTri() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "1");
        attributes.put("name", "   ");
        attributes.put("email", "");

        var social = SocialUserAttributes.of("facebook", attributes);

        assertThat(social.fullName()).isNull();
        assertThat(social.email()).isNull();
    }

    @Test
    void nhaCungCapChuaHoTroThiBaoLoi() {
        assertThatThrownBy(() -> SocialUserAttributes.of("instagram", Map.of("id", "1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("instagram");
    }
}
