package demo.socialanalytics.config;

import demo.socialanalytics.security.SocialLoginUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Đường dẫn công khai: trang đăng nhập, hai endpoint do Spring Security dựng sẵn cho
    // luồng OAuth2, tài nguyên tĩnh và tài liệu API.
    private static final String[] PUBLIC_PATHS = {
        "/login", "/error", "/css/**", "/js/**", "/favicon.ico",
        "/oauth2/**", "/login/oauth2/**",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SocialLoginUserService socialLoginUserService,
        // ObjectProvider: chưa cấu hình client id/secret thì Spring Boot không tạo
        // ClientRegistrationRepository. Lấy kiểu "có thì dùng" để app vẫn chạy được khi
        // chưa khai báo Social Login, thay vì chết ngay lúc khởi động.
        ObjectProvider<ClientRegistrationRepository> clientRegistrations
    ) throws Exception {

        http
            // CSRF BẬT (mặc định của Spring Security, ở đây khai báo tường minh cho rõ ý).
            // Token để trong cookie XSRF-TOKEN không đặt HttpOnly để JavaScript của dashboard
            // đọc được mà gắn vào header khi gọi API bằng fetch/axios.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

            // Từ Spring Security 6, token CSRF được nạp lười — không chỗ nào đọc tới thì cookie
            // không bao giờ được gửi về trình duyệt. Filter này chạm vào token ở mọi request
            // để cookie luôn có mặt ngay từ lần tải trang đầu tiên.
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())

            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(authenticationEntryPoint()))

            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("XSRF-TOKEN"));

        clientRegistrations.ifAvailable(repository ->
            applyOAuth2Login(http, socialLoginUserService, repository));

        return http.build();
    }

    private void applyOAuth2Login(
        HttpSecurity http,
        SocialLoginUserService socialLoginUserService,
        ClientRegistrationRepository clientRegistrationRepository) {
        try {
            http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                // Đăng nhập xong về thẳng dashboard. `true` để luôn về dashboard, kể cả khi
                // người dùng bấm đăng nhập từ một trang khác.
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(pkceResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo.userService(socialLoginUserService)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không cấu hình được OAuth2 Login", exception);
        }
    }

    // Bật PKCE cho luồng authorization code.
    //
    // Spring Security chỉ tự bật PKCE cho client kiểu "none" (không có client secret).
    // X/Twitter dùng client secret nhưng VẪN bắt buộc PKCE, nên phải bật tay ở đây.
    // Facebook cũng hỗ trợ PKCE nên bật chung cho cả hai, không hại gì.
    // Chưa đăng nhập thì trả về cái gì, tuỳ theo bên gọi là client API hay trình duyệt.
    //
    // Tự dựng DelegatingAuthenticationEntryPoint thay vì dùng defaultAuthenticationEntryPointFor():
    // hễ gọi authenticationEntryPoint() để đặt mặc định thì Spring BỎ QUA toàn bộ các mapping
    // khai bằng defaultAuthenticationEntryPointFor(), nên trộn hai cách là mất phần phân biệt.
    //
    // Đặt mặc định tường minh chứ không dựa vào oauth2Login(): chưa cấu hình Social Login thì
    // oauth2Login không được gọi, khi đó Spring dùng entry point mặc định trả 401 và người dùng
    // không mở nổi trang /login để đọc hướng dẫn cấu hình.
    private AuthenticationEntryPoint authenticationEntryPoint() {
        MediaTypeRequestMatcher jsonRequest = new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
        // Bỏ qua */*: trình duyệt luôn gửi kèm "*/*;q=0.8" ở cuối header Accept, mà */* thì
        // "tương thích" với application/json. Không loại ra thì mở trang bằng trình duyệt cũng
        // bị coi là gọi API và nhận 401 thay vì được chuyển tới trang đăng nhập.
        jsonRequest.setIgnoredMediaTypes(Set.of(MediaType.ALL));

        LinkedHashMap<org.springframework.security.web.util.matcher.RequestMatcher, AuthenticationEntryPoint>
            byRequestType = new LinkedHashMap<>();
        byRequestType.put(jsonRequest, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(byRequestType);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    private OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository repository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
            new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    // Đọc token CSRF ra để CsrfFilter ghi cookie XSRF-TOKEN cho trình duyệt.
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
