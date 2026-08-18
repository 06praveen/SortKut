package pravCode.SortKut.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pravCode.SortKut.entity.FileTransfer;
import pravCode.SortKut.service.FileTransferService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/transfer")
public class FileTransferRestController {

    private final FileTransferService fileTransferService;

    @Autowired
    public FileTransferRestController(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    @PostMapping
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("expiresIn") String expiresIn,
            @RequestParam("maxDownloads") Integer maxDownloads,
            @RequestParam(value = "password", required = false) String password) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Uploaded file cannot be empty.");
            }
            FileTransfer transfer = fileTransferService.saveFile(file, expiresIn, maxDownloads, password);
            return ResponseEntity.ok(Map.of("transferCode", transfer.getTransferCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload file: " + e.getMessage());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getFileMetadata(
            @PathVariable String code,
            @RequestParam(value = "password", required = false) String password) {
        Optional<FileTransfer> transferOpt = fileTransferService.getActiveTransfer(code);
        if (transferOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("This transfer code is invalid or has expired!");
        }

        FileTransfer transfer = transferOpt.get();

        // If password is set, verify it before exposing real file metadata
        if (transfer.getPassword() != null && !transfer.getPassword().isEmpty()) {
            if (password == null || !transfer.getPassword().equals(password.trim())) {
                return ResponseEntity.ok(Map.of("requiresPassword", true));
            }
        }

        return ResponseEntity.ok(Map.of(
                "fileName", transfer.getFileName(),
                "fileSize", transfer.getFileSize()
        ));
    }

    @GetMapping("/{code}/download")
    public ResponseEntity<?> downloadFile(
            @PathVariable String code,
            @RequestParam(value = "password", required = false) String password) {
        try {
            Optional<FileTransfer> transferOpt = fileTransferService.getActiveTransfer(code);
            if (transferOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("This transfer code is invalid or has expired!");
            }
            FileTransfer transfer = transferOpt.get();

            Path filePath = fileTransferService.getFileForDownload(code, password);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.internalServerError().body("File was not found on the server filesystem.");
            }

            // Parse actual MIME Type or fallback to octet-stream
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (transfer.getMimeType() != null && !transfer.getMimeType().isEmpty()) {
                try {
                    mediaType = MediaType.parseMediaType(transfer.getMimeType());
                } catch (Exception ignored) {}
            }

            // Stream file attachment
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + transfer.getFileName() + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(transfer.getFileSize()))
                    .body(resource);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body("Incorrect password for this transfer.");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to read the file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
