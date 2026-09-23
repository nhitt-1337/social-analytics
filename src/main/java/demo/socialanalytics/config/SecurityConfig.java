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

    // Đường dẫn công khai: trang đăng nhập
    private static final String[] PUBLIC_PATHS = {
        "/login", "/error", "/css/**", "/js/**", "/favicon.ico",
        "/oauth2/**", "/login/oauth2/**",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
        // Công khai vì client SOAP không có phiên đăng nhập; chỉ lộ số liệu gộp
        WebServiceConfig.PATH + "/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SocialLoginUserService socialLoginUserService,
        // ObjectProvider: chưa cấu hình client id/secret thì Spring Boot không tạo
        ObjectProvider<ClientRegistrationRepository> clientRegistrations
    ) throws Exception {

        http
            // CSRF BẬT (mặc định của Spring Security, ở đây khai báo tường minh cho rõ ý).
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // Bỏ CSRF cho endpoint SOAP: client SOAP gửi POST và không có khái niệm token CSRF.
                .ignoringRequestMatchers(WebServiceConfig.PATH + "/**")
                // Bỏ CSRF cho endpoint WebSocket.
                .ignoringRequestMatchers(WebSocketConfig.ENDPOINT + "/**"))

            // Từ Spring Security 6 token CSRF nạp lười: không chạm tới thì cookie không được gửi
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
                // Đăng nhập xong về thẳng dashboard.
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(pkceResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo.userService(socialLoginUserService)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không cấu hình được OAuth2 Login", exception);
        }
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        MediaTypeRequestMatcher jsonRequest = new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
        // Bỏ */*: trình duyệt luôn gửi kèm, không loại thì mở trang cũng bị coi là gọi API
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
