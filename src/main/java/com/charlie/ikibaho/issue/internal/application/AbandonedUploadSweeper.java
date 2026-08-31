package com.charlie.ikibaho.issue.internal.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Removes uploads that were requested and never completed.
 * <p>
 * Locked, because deleting the same rows from three instances at once produces
 * two failures and one success for no benefit.
 */
@Component
class AbandonedUploadSweeper {
    private final AttachmentService attachments;
    private final Duration abandonAfter;

    AbandonedUploadSweeper(AttachmentService attachments,
                           @Value("${ikibaho.storage.abandon-uploads-after:PT2H}") Duration abandonAfter) {
        this.attachments = attachments;
        this.abandonAfter = abandonAfter;
    }

    /**
     * Hourly, well clear of the 15-minute upload window -- a slow client on a bad
     * connection must not have its in-flight upload swept out from under it.
     */
    @Scheduled(cron = "${ikibaho.storage.sweep-cron:0 15 * * * *}")
    @SchedulerLock(name = "sweepAbandonedUploads", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    void sweep() {
        attachments.sweepAbandonedUploads(abandonAfter);
    }
}
