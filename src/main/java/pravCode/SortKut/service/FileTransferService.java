package pravCode.SortKut.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pravCode.SortKut.entity.FileTransfer;
import pravCode.SortKut.repository.FileTransferRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FileTransferService {

    private final FileTransferRepository fileTransferRepository;
    private final SecureRandom random = new SecureRandom();
    private final Path uploadDirectory = Paths.get("uploads").toAbsolutePath().normalize();

    @Autowired
    public FileTransferService(FileTransferRepository fileTransferRepository) {
        this.fileTransferRepository = fileTransferRepository;
        // Ensure upload directory exists
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload folder directory!", e);
        }
    }

    @Transactional
    public FileTransfer saveFile(MultipartFile file, String expiresIn, Integer maxDownloads, String password) throws IOException {
        String transferCode;
        // Generate a unique 6-digit numeric transfer code, checking for database collisions
        do {
            transferCode = generateNumericCode(6);
        } while (fileTransferRepository.findByTransferCode(transferCode).isPresent());

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isEmpty()) {
            originalFileName = "unnamed_file";
        }

        // Clean filename of path traversal characters
        originalFileName = Paths.get(originalFileName).getFileName().toString();

        // Create a unique name on the disk to avoid namespace conflicts
        String storageFileName = transferCode + "_" + originalFileName;
        Path targetPath = this.uploadDirectory.resolve(storageFileName);

        // Copy file stream to targeted path
        Files.copy(file.getInputStream(), targetPath);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = calculateExpiration(expiresIn, now);

        FileTransfer transfer = FileTransfer.builder()
                .transferCode(transferCode)
                .fileName(originalFileName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .storagePath(targetPath.toString())
                .createdAt(now)
                .expiresAt(expiresAt)
                .downloadCount(0)
                .maxDownloads(maxDownloads == null ? 1 : maxDownloads)
                .password(password != null && !password.trim().isEmpty() ? password.trim() : null)
                .build();

        return fileTransferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public Optional<FileTransfer> getActiveTransfer(String transferCode) {
        Optional<FileTransfer> transferOpt = fileTransferRepository.findByTransferCode(transferCode);
        if (transferOpt.isPresent()) {
            FileTransfer transfer = transferOpt.get();

            // Expired check
            if (transfer.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty();
            }

            // Download bounds limit check
            if (transfer.getMaxDownloads() != -1 && transfer.getDownloadCount() >= transfer.getMaxDownloads()) {
                return Optional.empty();
            }

            return Optional.of(transfer);
        }
        return Optional.empty();
    }

    @Transactional
    public Path getFileForDownload(String transferCode, String providedPassword) throws IOException {
        Optional<FileTransfer> transferOpt = getActiveTransfer(transferCode);
        if (transferOpt.isEmpty()) {
            throw new IllegalArgumentException("This transfer code is invalid or has expired!");
        }

        FileTransfer transfer = transferOpt.get();

        // Check password protection
        if (transfer.getPassword() != null && !transfer.getPassword().isEmpty()) {
            if (providedPassword == null || !transfer.getPassword().equals(providedPassword.trim())) {
                throw new SecurityException("Incorrect password provided for this transfer!");
            }
        }

        Path filePath = Paths.get(transfer.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new IOException("The physical file is missing from the server storage!");
        }

        // Increment download count
        transfer.setDownloadCount(transfer.getDownloadCount() + 1);
        fileTransferRepository.save(transfer);

        return filePath;
    }

    /**
     * Automated physical file and database cleaner. Runs every 5 minutes.
     * Deletes physical files first, then deletes corresponding database records.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void purgeExpiredTransfers() {
        LocalDateTime now = LocalDateTime.now();
        List<FileTransfer> purgeable = fileTransferRepository.findPurgeableTransfers(now);

        for (FileTransfer transfer : purgeable) {
            try {
                Path filePath = Paths.get(transfer.getStoragePath());
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {}
            fileTransferRepository.delete(transfer);
        }
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private LocalDateTime calculateExpiration(String expiresIn, LocalDateTime now) {
        if (expiresIn == null) return now.plusHours(24);
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
