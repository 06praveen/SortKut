package pravCode.SortKut.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pravCode.SortKut.entity.FileTransfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileTransferRepository extends JpaRepository<FileTransfer, Long> {

    Optional<FileTransfer> findByTransferCode(String transferCode);

    @Query("SELECT f FROM FileTransfer f WHERE f.expiresAt < :now OR (f.maxDownloads != -1 AND f.downloadCount >= f.maxDownloads)")
    List<FileTransfer> findPurgeableTransfers(LocalDateTime now);
}
