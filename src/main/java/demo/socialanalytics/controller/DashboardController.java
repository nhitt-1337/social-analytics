package demo.socialanalytics.controller;

import demo.socialanalytics.security.SocialLoginUserService;
import demo.socialanalytics.service.CrawlStatusService;
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
@Hidden
@Controller
public class DashboardController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final CrawlStatusService crawlStatusService;

    public DashboardController(
        ObjectProvider<ClientRegistrationRepository> clientRegistrations,
        CrawlStatusService crawlStatusService
    ) {
        this.clientRegistrations = clientRegistrations;
        this.crawlStatusService = crawlStatusService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    // Trang đăng nhập tự dựng (thay trang mặc định của Spring Security) để đặt đúng hai nút
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("providers", availableProviders());
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        model.addAttribute("principal", principal);
        // Rỗng khi job chưa chạy lần nào -> template hiện "chưa cập nhật lần nào".
        model.addAttribute("lastRun", crawlStatusService.lastRun().orElse(null));
        return "dashboard";
    }

    // Endpoint để thử CSRF: form ở dashboard POST vào đây.
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
