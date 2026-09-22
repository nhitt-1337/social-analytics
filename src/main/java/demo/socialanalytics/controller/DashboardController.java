package demo.socialanalytics.controller;

import demo.socialanalytics.security.SocialLoginUserService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Trả về trang HTML (Thymeleaf), khác với các @RestController trả JSON.
// @Hidden để mấy trang này không lẫn vào tài liệu OpenAPI của API.
@Hidden
@Controller
public class DashboardController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public DashboardController(ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.clientRegistrations = clientRegistrations;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    // Trang đăng nhập tự dựng (thay trang mặc định của Spring Security) để đặt đúng hai nút
    // Facebook / X và hiển thị thông báo lỗi bằng tiếng Việt.
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("providers", availableProviders());
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        model.addAttribute("principal", principal);
        return "dashboard";
    }

    // Endpoint để thử CSRF: form ở dashboard POST vào đây.
    // Không kèm token hợp lệ thì CsrfFilter chặn trước khi tới được method này (403).
    @PostMapping("/dashboard/note")
    public String saveNote(
        @RequestParam(defaultValue = "") String note,
        @AuthenticationPrincipal OAuth2User principal,
        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes
    ) {
        String who = principal == null ? "?" : String.valueOf(principal.getAttribute(
            SocialLoginUserService.PRINCIPAL_ATTRIBUTE));
        redirectAttributes.addFlashAttribute("noteResult",
            "Đã nhận ghi chú của " + who + ": \"" + note + "\" (token CSRF hợp lệ)");
        return "redirect:/dashboard";
    }

    // Chỉ hiện nút của nhà cung cấp đã được cấu hình client id/secret.
    private List<Map<String, String>> availableProviders() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        List<Map<String, String>> result = new ArrayList<>();
        if (!(repository instanceof InMemoryClientRegistrationRepository registrations)) {
            return result;
        }
        for (ClientRegistration registration : registrations) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", registration.getRegistrationId());
            item.put("name", registration.getClientName());
            result.add(item);
        }
        return result;
    }
}
