package pravCode.SortKut.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pravCode.SortKut.entity.ShortUrl;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    @Modifying
    @Transactional
    @Query("DELETE FROM ShortUrl s WHERE s.expiresAt < :now")
    void deleteExpiredUrls(LocalDateTime now);
}
