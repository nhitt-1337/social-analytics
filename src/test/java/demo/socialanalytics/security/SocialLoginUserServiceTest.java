package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;
import demo.socialanalytics.entity.Role;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Kiểm tra phần "lưu thông tin user" khi đăng nhập.
@ExtendWith(MockitoExtension.class)
class SocialLoginUserServiceTest {

    @Mock UserRepository userRepository;

    SocialLoginUserService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginUserService(userRepository);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 1L);
            }
            return saved;
        });
    }

    // upsert là private; gọi qua Reflection để test được quyết định nghiệp vụ mà không phải nới
    private User upsert(SocialUserAttributes social) {
        try {
            Method method = SocialLoginUserService.class
                .getDeclaredMethod("upsert", SocialUserAttributes.class);
            method.setAccessible(true);
            return (User) method.invoke(service, social);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private SocialUserAttributes facebook(String id, String name, String email) {
        return new SocialUserAttributes(AuthProvider.FACEBOOK, id, name, email,
            "https://graph.facebook.com/" + id + "/picture?type=large");
    }

    private SocialUserAttributes x(String id, String name) {
        return new SocialUserAttributes(AuthProvider.TWITTER, id, name, null, null);
    }

    @Test
    void dangNhapLanDauThiTaoTaiKhoanMoi() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-1"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());

        upsert(facebook("fb-1", "Nguyễn Văn A", "a@example.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.FACEBOOK);
        assertThat(saved.getProviderId()).isEqualTo("fb-1");
        assertThat(saved.getEmail()).isEqualTo("a@example.com");
        assertThat(saved.getFullName()).isEqualTo("Nguyễn Văn A");
        // Người đăng nhập qua mạng xã hội chỉ là USER, không tự động thành ADMIN.
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void dangNhapLanSauThiCapNhatHoSo() {
        User existing = new User();
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.setProvider(AuthProvider.FACEBOOK);
        existing.setProviderId("fb-1");
        existing.setFullName("Tên cũ");
        existing.setEmail("a@example.com");
        existing.setRole(Role.ADMIN);
        when(userRepository.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-1"))
            .thenReturn(Optional.of(existing));

        User result = upsert(facebook("fb-1", "Tên mới", "a@example.com"));

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getFullName()).isEqualTo("Tên mới");
        // Không hạ quyền người đã là ADMIN chỉ vì họ đăng nhập lại.
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).findByEmail(any());
    }

    // X không trả email -> không được ghi đè email cũ thành null.
    @Test
    void khongXoaEmailCuKhiNhaCungCapKhongTraEmail() {
        User existing = new User();
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.setProvider(AuthProvider.TWITTER);
        existing.setProviderId("x-1");
        existing.setEmail("cu@example.com");
        existing.setFullName("Tên cũ");
        existing.setRole(Role.USER);
        when(userRepository.findByProviderAndProviderId(AuthProvider.TWITTER, "x-1"))
            .thenReturn(Optional.of(existing));

        User result = upsert(x("x-1", "Tên mới"));

        assertThat(result.getEmail()).isEqualTo("cu@example.com");
    }

    @Test
    void taiKhoanXKhongCoEmailVanTaoDuoc() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.TWITTER, "x-1"))
            .thenReturn(Optional.empty());

        User result = upsert(x("x-1", "Trần Thị B"));

        assertThat(result.getEmail()).isNull();
        assertThat(result.getProvider()).isEqualTo(AuthProvider.TWITTER);
        // Không tra theo email vì không có email để tra.
        verify(userRepository, never()).findByEmail(any());
    }

    // Đã có sẵn tài khoản LOCAL cùng email -> gắn danh tính Facebook vào chính tài khoản đó
    @Test
    void ganDanhTinhVaoTaiKhoanLocalCungEmail() {
        User local = new User();
        ReflectionTestUtils.setField(local, "id", 5L);
        local.setEmail("admin@example.com");
        local.setFullName("Quản trị viên");
        local.setProvider(AuthProvider.LOCAL);
        local.setRole(Role.ADMIN);
        when(userRepository.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-9"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(local));

        User result = upsert(facebook("fb-9", "Quản trị viên", "admin@example.com"));

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getProvider()).isEqualTo(AuthProvider.FACEBOOK);
        assertThat(result.getProviderId()).isEqualTo("fb-9");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
    }

    // Email đó đã thuộc về một tài khoản mạng xã hội KHÁC -> không gắn đè, tạo tài khoản riêng
    @Test
    void khongGanDeLenTaiKhoanDaLienKetNhaCungCapKhac() {
        User other = new User();
        ReflectionTestUtils.setField(other, "id", 5L);
        other.setEmail("a@example.com");
        other.setProvider(AuthProvider.TWITTER);
        other.setProviderId("x-99");
        other.setRole(Role.USER);
        when(userRepository.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-9"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(other));

        User result = upsert(facebook("fb-9", "Người khác", "a@example.com"));

        assertThat(result.getId()).isNotEqualTo(5L);
        assertThat(result.getProviderId()).isEqualTo("fb-9");
    }

    @Test
    void khongGhiDeTenBangGiaTriRong() {
        User existing = new User();
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.setProvider(AuthProvider.FACEBOOK);
        existing.setProviderId("fb-1");
        existing.setFullName("Tên cũ");
        existing.setRole(Role.USER);
        when(userRepository.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-1"))
            .thenReturn(Optional.of(existing));

        User result = upsert(new SocialUserAttributes(
            AuthProvider.FACEBOOK, "fb-1", null, null, null));

        assertThat(result.getFullName()).isEqualTo("Tên cũ");
    }

    // Thuộc tính đưa vào phiên đăng nhập: chỉ những gì dashboard cần, không bê nguyên payload.
    @Test
    void khoaPrincipalLaDinhDanhGomNhaCungCapVaId() {
        assertThat(SocialLoginUserService.PRINCIPAL_ATTRIBUTE).isEqualTo("principal");
        assertThat(facebook("fb-1", "A", "a@example.com").principalName()).isEqualTo("facebook:fb-1");
    }
}
