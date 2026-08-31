package com.charlie.ikibaho.integration.internal.persistence;

import com.charlie.ikibaho.integration.internal.domain.WebhookDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    /**
     * Claims a batch of due deliveries.
     * <p>
     * SKIP LOCKED, so two instances that both wake up take different rows instead
     * of one blocking on the other. ShedLock already makes the job singular, but
     * this keeps the query correct on its own -- a lock that expires mid-run must
     * not turn into duplicate deliveries.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT d FROM WebhookDelivery d
            WHERE d.state = com.charlie.ikibaho.integration.internal.domain.WebhookDelivery$State.PENDING
              AND d.nextAttemptAt <= :now
            ORDER BY d.nextAttemptAt ASC
            """)
    List<WebhookDelivery> claimDue(Instant now, Pageable pageable);

    List<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId, Pageable pageable);

    long countByWebhookIdAndState(UUID webhookId, WebhookDelivery.State state);
}
