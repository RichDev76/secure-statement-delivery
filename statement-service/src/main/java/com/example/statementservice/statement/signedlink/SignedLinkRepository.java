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
}
