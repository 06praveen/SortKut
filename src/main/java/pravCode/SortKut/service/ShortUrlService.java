package pravCode.SortKut.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pravCode.SortKut.dto.UrlShortenRequest;
import pravCode.SortKut.entity.ShortUrl;
import pravCode.SortKut.repository.ShortUrlRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // System reserved keywords that should not be used as custom aliases
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "api", "p", "css", "js", "images", "error", "static", "templates"
    );

    @Autowired
    public ShortUrlService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    @Transactional
    public ShortUrl createShortUrl(UrlShortenRequest request) {
        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().trim().isEmpty()) {
            String alias = request.getCustomAlias().trim();
            // Validate alias characters
            if (!alias.matches("^[a-zA-Z0-9_-]+$")) {
                throw new IllegalArgumentException("Custom alias can only contain letters, numbers, hyphens, and underscores.");
            }
            // Check reserved keywords
            if (RESERVED_KEYWORDS.contains(alias.toLowerCase())) {
                throw new IllegalArgumentException("This custom alias is a reserved keyword and cannot be used.");
            }
            // Check availability
            if (shortUrlRepository.findByShortCode(alias).isPresent()) {
                throw new IllegalArgumentException("This custom alias is already taken.");
            }
            shortCode = alias;
        } else {
            // Generate a unique 6-character short code, checking for database collisions
            do {
                shortCode = generateRandomCode(6);
            } while (shortUrlRepository.findByShortCode(shortCode).isPresent());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = calculateExpiration(request.getExpiresIn(), now);

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl().trim())
                .password(request.getPassword() != null && !request.getPassword().trim().isEmpty() ? request.getPassword().trim() : null)
                .createdAt(now)
                .expiresAt(expiresAt)
                .clickCount(0L)
                .clicksToday(0L)
                .build();

        return shortUrlRepository.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public Optional<ShortUrl> getActiveShortUrl(String shortCode) {
        Optional<ShortUrl> urlOpt = shortUrlRepository.findByShortCode(shortCode);
        if (urlOpt.isPresent()) {
            ShortUrl url = urlOpt.get();
            if (url.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty(); // Treated as not found if expired
            }
            return Optional.of(url);
        }
        return Optional.empty();
    }

    @Transactional
    public void incrementClicks(ShortUrl shortUrl) {
        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
        shortUrl.setClicksToday(shortUrl.getClicksToday() + 1);
        shortUrl.setLastClickAt(LocalDateTime.now());
        shortUrlRepository.save(shortUrl);
    }

    /**
     * Scheduled task to clean up expired short URLs from the database every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void purgeExpiredUrls() {
        shortUrlRepository.deleteExpiredUrls(LocalDateTime.now());
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private LocalDateTime calculateExpiration(String expiresIn, LocalDateTime now) {
        if (expiresIn == null) {
            return now.plusHours(24);
        }
        return switch (expiresIn.trim()) {
            case "5m" -> now.plusMinutes(5);
            case "10m" -> now.plusMinutes(10);
            case "30m" -> now.plusMinutes(30);
            case "1h" -> now.plusHours(1);
            case "12h" -> now.plusHours(12);
            case "24h" -> now.plusHours(24);
            default -> now.plusHours(24);
        };
    }
}
