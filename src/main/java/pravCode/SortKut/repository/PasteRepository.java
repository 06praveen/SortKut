package pravCode.SortKut.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pravCode.SortKut.entity.Paste;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasteRepository extends JpaRepository<Paste, Long> {

    Optional<Paste> findBySlug(String slug);

    @Modifying
    @Transactional
    @Query("DELETE FROM Paste p WHERE p.expiresAt < :now")
    void deleteExpiredPastes(LocalDateTime now);
}
