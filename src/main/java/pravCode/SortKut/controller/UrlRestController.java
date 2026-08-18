package pravCode.SortKut.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pravCode.SortKut.dto.UrlShortenRequest;
import pravCode.SortKut.dto.UrlShortenResponse;
import pravCode.SortKut.entity.ShortUrl;
import pravCode.SortKut.service.ShortUrlService;

@RestController
@RequestMapping("/api/url/shorten")
public class UrlRestController {

    private final ShortUrlService shortUrlService;

    @Autowired
    public UrlRestController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping
    public ResponseEntity<?> shortenUrl(@Valid @RequestBody UrlShortenRequest request) {
        try {
            ShortUrl shortUrl = shortUrlService.createShortUrl(request);
            UrlShortenResponse response = UrlShortenResponse.builder()
                    .shortCode(shortUrl.getShortCode())
                    .clickCount(shortUrl.getClickCount())
                    .clicksToday(shortUrl.getClicksToday())
                    .lastClickAt(shortUrl.getLastClickAt())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to shorten URL: " + e.getMessage());
        }
    }

    @GetMapping("/{code}/info")
    public ResponseEntity<?> getUrlInfo(@PathVariable String code) {
        try {
            java.util.Optional<ShortUrl> urlOpt = shortUrlService.getActiveShortUrl(code);
            if (urlOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            ShortUrl shortUrl = urlOpt.get();
            UrlShortenResponse response = UrlShortenResponse.builder()
                    .shortCode(shortUrl.getShortCode())
                    .clickCount(shortUrl.getClickCount())
                    .clicksToday(shortUrl.getClicksToday())
                    .lastClickAt(shortUrl.getLastClickAt())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to retrieve statistics: " + e.getMessage());
        }
    }
}
