package com.charlie.ikibaho.integration.internal.delivery;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Drains the delivery queue on a schedule.
 * <p>
 * Scheduling and locking only -- the work itself lives in
 * {@link WebhookDeliveryRunner}, so that its transactions go through a proxy
 * rather than being bypassed by a self-call.
 */
@Component
public class WebhookDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryJob.class);

    private final WebhookDeliveryRunner runner;
    private final int batchSize;

    WebhookDeliveryJob(WebhookDeliveryRunner runner,
                       @Value("${ikibaho.webhooks.batch-size:50}") int batchSize) {
        this.runner = runner;
        this.batchSize = batchSize;
    }

    /**
     * Locked so a multi-instance deployment delivers each webhook once rather
     * than once per node.
     */
    @Scheduled(fixedDelayString = "${ikibaho.webhooks.poll-interval:10000}")
    @SchedulerLock(name = "webhookDelivery", lockAtLeastFor = "PT5S", lockAtMostFor = "PT5M")
    public void deliverDue() {
        drainNow();
    }

    /**
     * The same work without the cluster lock.
     * <p>
     * Tests call this rather than {@link #deliverDue()}: ShedLock's
     * {@code lockAtLeastFor} deliberately suppresses a second run for five
     * seconds, which is right in production and useless in a test that wants to
     * drain the queue now. ShedLock's own behaviour is not what these tests are
     * checking.
     */
    public int drainNow() {
        List<UUID> due = runner.claimDue(batchSize);
        if (due.isEmpty()) {
            return 0;
        }
        log.debug("Delivering {} webhook(s)", due.size());
        for (UUID deliveryId : due) {
            runner.deliverOne(deliveryId);
        }
        return due.size();
    }
}
