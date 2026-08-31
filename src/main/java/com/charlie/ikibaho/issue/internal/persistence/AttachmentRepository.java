package com.charlie.ikibaho.issue.internal.persistence;

import com.charlie.ikibaho.issue.internal.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByIssueIdAndStatusOrderByCreatedAtAsc(UUID issueId, Attachment.Status status);

    List<Attachment> findByIssueId(UUID issueId);

    /**
     * Uploads that were promised and never arrived.
     * <p>
     * A presigned URL that nobody used leaves a PENDING row forever. Sweeping
     * them keeps the attachment list honest -- a file listed but never uploaded
     * is worse than one that was never listed.
     */
    List<Attachment> findByStatusAndCreatedAtBefore(Attachment.Status status, Instant cutoff);
}
