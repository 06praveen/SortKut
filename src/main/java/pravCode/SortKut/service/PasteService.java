package pravCode.SortKut.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pravCode.SortKut.dto.PasteRequest;
import pravCode.SortKut.entity.Paste;
import pravCode.SortKut.repository.PasteRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PasteService {

    private final PasteRepository pasteRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Autowired
    public PasteService(PasteRepository pasteRepository) {
        this.pasteRepository = pasteRepository;
    }

    @Transactional
    public Paste createPaste(PasteRequest request) {
        String slug;
        // Generate a unique 7-character slug, checking for collisions to ensure high load safety
        do {
            slug = generateSlug(7);
        } while (pasteRepository.findBySlug(slug).isPresent());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = calculateExpiration(request.getExpiresIn(), now);

        Paste paste = Paste.builder()
                .slug(slug)
                .title(request.getTitle() != null && !request.getTitle().trim().isEmpty() ? request.getTitle().trim() : null)
                .content(request.getContent())
                .language(request.getLanguage() != null ? request.getLanguage().toLowerCase() : "plain")
                .password(request.getPassword() != null && !request.getPassword().trim().isEmpty() ? request.getPassword().trim() : null)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        return pasteRepository.save(paste);
    }

    @Transactional(readOnly = true)
    public Optional<Paste> getPasteBySlug(String slug) {
        Optional<Paste> pasteOpt = pasteRepository.findBySlug(slug);
        if (pasteOpt.isPresent()) {
            Paste paste = pasteOpt.get();
            if (paste.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty(); // Treated as not found if expired
            }
            return Optional.of(paste);
        }
        return Optional.empty();
    }

    /**
     * Background cleaner scheduled task running every 5 minutes.
     * Keeps database lightweight and lookup indexes extremely fast under heavy concurrent load.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void purgeExpiredPastes() {
        pasteRepository.deleteExpiredPastes(LocalDateTime.now());
    }

    private String generateSlug(int length) {
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
