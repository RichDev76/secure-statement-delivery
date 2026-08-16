package com.example.statementservice.statement.signedlink;

import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignedLinkRepository extends JpaRepository<SignedLink, UUID> {

    Optional<SignedLink> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Transactional
    @Query(
            value = "WITH rows_to_delete AS (" + "    SELECT id FROM signed_links "
                    + "    WHERE (expires_at < :cutoff) "
                    + "    ORDER BY expires_at ASC "
                    + "    LIMIT :batchSize"
                    + ") "
                    + "DELETE FROM signed_links "
                    + "WHERE id IN (SELECT id FROM rows_to_delete)",
            nativeQuery = true)
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);

    // Atomic conditional increment: returns 0 (no rows affected) once maxRedemptions is already
    // reached, 1 otherwise - the same "rows-affected as the signal" idiom deleteExpired uses.
    @Modifying
    @Transactional
    @Query(
            value = "UPDATE signed_links SET redemption_count = redemption_count + 1 "
                    + "WHERE id = :id AND redemption_count < :maxRedemptions",
            nativeQuery = true)
    int recordRedemption(@Param("id") UUID id, @Param("maxRedemptions") int maxRedemptions);
}
