package com.charlie.ikibaho.notification.internal.persistence;

import com.charlie.ikibaho.notification.internal.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdOrderByOccurredAtDesc(UUID recipientId, Pageable pageable);

    List<Notification> findByRecipientIdAndReadAtIsNullOrderByOccurredAtDesc(UUID recipientId,
                                                                             Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByDedupeKey(String dedupeKey);

    /**
     * Bulk mark-as-read in one statement.
     * <p>
     * Loading every unread row to call markRead on each would be an unbounded
     * read for a write that touches no domain rule. The WHERE clause keeps it
     * idempotent -- already-read rows are not restamped.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.recipientId = :recipientId AND n.readAt IS NULL
            """)
    int markAllRead(UUID recipientId, Instant readAt);

    /**
     * Everyone with unread notifications newer than their last digest.
     */
    @Query("""
            SELECT DISTINCT n.recipientId FROM Notification n
            WHERE n.readAt IS NULL AND n.occurredAt > :since
            """)
    List<UUID> recipientsWithUnreadSince(Instant since);

    List<Notification> findByRecipientIdAndReadAtIsNullAndOccurredAtAfterOrderByOccurredAtAsc(
            UUID recipientId, Instant since);
}
