package pravCode.SortKut.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pravCode.SortKut.entity.Paste;
import pravCode.SortKut.service.PasteService;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
public class HomeController {

    private final PasteService pasteService;
    private final pravCode.SortKut.service.ShortUrlService shortUrlService;
    private final DateTimeFormatter expiryFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Autowired
    public HomeController(PasteService pasteService, pravCode.SortKut.service.ShortUrlService shortUrlService) {
        this.pasteService = pasteService;
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/p/{slug}")
    public String viewPaste(@PathVariable String slug, HttpServletRequest request, HttpSession session, Model model) {
        Optional<Paste> pasteOpt = pasteService.getPasteBySlug(slug);

        if (pasteOpt.isEmpty()) {
            model.addAttribute("errorMsg", "This paste does not exist or has expired! 📭");
            return "index";
        }

        Paste paste = pasteOpt.get();

        // Check if paste is password protected
        if (paste.getPassword() != null && !paste.getPassword().isEmpty()) {
            Boolean unlocked = (Boolean) session.getAttribute("unlocked_paste_" + slug);
            if (unlocked == null || !unlocked) {
                model.addAttribute("requiresPassword", true);
                model.addAttribute("pasteSlug", slug);
                return "index";
            }
        }

        // If not protected or successfully verified:
        model.addAttribute("viewPaste", true);
        model.addAttribute("pasteSlug", slug);
        model.addAttribute("pasteTitle", paste.getTitle() != null ? paste.getTitle() : "Untitled Paste");
        model.addAttribute("pasteContent", paste.getContent());
        model.addAttribute("pasteLang", paste.getLanguage());
        model.addAttribute("pasteExpires", paste.getExpiresAt().format(expiryFormatter));
        model.addAttribute("requestUrl", request.getRequestURL().toString());
        return "index";
    }

    @PostMapping("/p/{slug}/verify")
    public String verifyPassword(@PathVariable String slug, @RequestParam String password, HttpSession session, Model model) {
        Optional<Paste> pasteOpt = pasteService.getPasteBySlug(slug);

        if (pasteOpt.isEmpty()) {
            model.addAttribute("errorMsg", "This paste does not exist or has expired! 📭");
            return "index";
        }

        Paste paste = pasteOpt.get();

        if (paste.getPassword() != null && paste.getPassword().equals(password.trim())) {
            // Unlock standard session-level authorization
            session.setAttribute("unlocked_paste_" + slug, true);
            return "redirect:/p/" + slug;
        } else {
            model.addAttribute("requiresPassword", true);
            model.addAttribute("pasteSlug", slug);
            model.addAttribute("errorMsg", "Incorrect password! Please try again. 🔑");
            return "index";
        }
    }

    @GetMapping("/{code:[a-zA-Z0-9_-]+}")
    public String redirectShortUrl(@PathVariable String code, HttpSession session, Model model) {
        // Skip common static resource directories and system endpoints
        if (code.equals("error") || code.equals("swagger-ui") || code.equals("v3")) {
            return "forward:/error";
        }

        java.util.Optional<pravCode.SortKut.entity.ShortUrl> urlOpt = shortUrlService.getActiveShortUrl(code);

        if (urlOpt.isEmpty()) {
            model.addAttribute("errorMsg", "This short URL does not exist or has expired! 📭");
            return "index";
        }

        pravCode.SortKut.entity.ShortUrl url = urlOpt.get();

        // Check password protection
        if (url.getPassword() != null && !url.getPassword().isEmpty()) {
            Boolean unlocked = (Boolean) session.getAttribute("unlocked_url_" + code);
            if (unlocked == null || !unlocked) {
                model.addAttribute("requiresUrlPassword", true);
                model.addAttribute("shortCode", code);
                return "index";
            }
        }

        // Increment clicks and redirect
        shortUrlService.incrementClicks(url);
        return "redirect:" + url.getOriginalUrl();
    }

    @PostMapping("/{code}/verify")
    public String verifyUrlPassword(@PathVariable String code, @RequestParam String password, HttpSession session, Model model) {
        java.util.Optional<pravCode.SortKut.entity.ShortUrl> urlOpt = shortUrlService.getActiveShortUrl(code);

        if (urlOpt.isEmpty()) {
            model.addAttribute("errorMsg", "This short URL does not exist or has expired! 📭");
            return "index";
        }

        pravCode.SortKut.entity.ShortUrl url = urlOpt.get();

        if (url.getPassword() != null && url.getPassword().equals(password.trim())) {
            session.setAttribute("unlocked_url_" + code, true);
            return "redirect:/" + code;
        } else {
            model.addAttribute("requiresUrlPassword", true);
            model.addAttribute("shortCode", code);
            model.addAttribute("errorMsg", "Incorrect password! Please try again. 🔑");
            return "index";
        }
    }
}
