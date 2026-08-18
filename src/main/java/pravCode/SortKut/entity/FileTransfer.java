package pravCode.SortKut.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_transfers", indexes = {
    @Index(name = "idx_transfer_code", columnList = "transferCode", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String transferCode;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(length = 255)
    private String mimeType;

    @Column(nullable = false, length = 1024)
    private String storagePath;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer downloadCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxDownloads = 1; // -1 for unlimited

    @Column(length = 255)
    private String password;
}
