package com.charlie.ikibaho.issue.internal.application;

import com.charlie.ikibaho.issue.internal.domain.Attachment;
import com.charlie.ikibaho.issue.internal.persistence.AttachmentRepository;
import com.charlie.ikibaho.issue.internal.web.dto.AttachmentResponse;
import com.charlie.ikibaho.issue.internal.web.dto.UploadTicket;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The two-step upload.
 *
 * <ol>
 *   <li>{@link #requestUpload} records the intent and hands back a presigned URL.</li>
 *   <li>The client PUTs the bytes straight to storage. The server sees none of it.</li>
 *   <li>{@link #completeUpload} asks storage what actually arrived and publishes
 *       the attachment.</li>
 * </ol>
 * <p>
 * The third step is not a formality. Without it the attachment list would show
 * files that were never uploaded, at whatever size the client felt like claiming.
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /**
     * Anything a browser will happily execute in the origin's context.
     */
    private static final List<String> FORBIDDEN_TYPES = List.of(
            "text/html", "application/xhtml+xml", "image/svg+xml", "application/xml", "text/xml");

    private final AttachmentRepository attachments;
    private final StorageService storage;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;
    private final long maxBytes;
    private final Duration uploadTtl;
    private final Duration downloadTtl;

    AttachmentService(AttachmentRepository attachments, StorageService storage,
                      PermissionService permissions, IssueContextResolver contexts,
                      @Value("${ikibaho.storage.max-upload-bytes:26214400}") long maxBytes,
                      @Value("${ikibaho.storage.upload-url-ttl:PT15M}") Duration uploadTtl,
                      @Value("${ikibaho.storage.download-url-ttl:PT5M}") Duration downloadTtl) {
        this.attachments = attachments;
        this.storage = storage;
        this.permissions = permissions;
        this.contexts = contexts;
        this.maxBytes = maxBytes;
        this.uploadTtl = uploadTtl;
        this.downloadTtl = downloadTtl;
    }

    private static AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getIssueId(), a.getFilename(),
                a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getCreatedAt());
    }

    @Transactional
    public UploadTicket requestUpload(UUID issueId, UUID actorId, String filename,
                                      String contentType, long declaredSize) {
        IssueContext ctx = requireContext(issueId);
        permissions.require(actorId, ctx, Permission.EDIT_ISSUE);

        String safeName = validateFilename(filename);
        String safeType = validateContentType(contentType);

        // Checked before a URL is issued, not after the bytes arrive. A presigned
        // PUT cannot enforce a size limit, so the only cheap moment to refuse an
        // oversized upload is before handing out permission to make it.
        if (declaredSize <= 0 || declaredSize > maxBytes) {
            throw new ValidationException(
                    "Attachments must be between 1 byte and " + maxBytes + " bytes");
        }

        // Random key, not the filename: two people uploading "screenshot.png" must
        // not collide, and the key must not leak the original name to anyone who
        // can see a URL.
        String objectKey = "attachments/" + ctx.projectId() + '/' + issueId + '/' + UUID.randomUUID();

        Attachment attachment = attachments.save(new Attachment(
                issueId, ctx.projectId(), safeName, safeType, declaredSize, objectKey, actorId));

        StorageService.PresignedUrl url = storage.presignUpload(objectKey, safeType, uploadTtl);

        return new UploadTicket(attachment.getId(), url.url(), url.expiresAt(), safeType);
    }

    @Transactional
    public AttachmentResponse completeUpload(UUID attachmentId, UUID actorId) {
        Attachment attachment = load(attachmentId);
        permissions.require(actorId, requireContext(attachment.getIssueId()), Permission.EDIT_ISSUE);

        StorageService.StoredObject stored = storage.head(attachment.getObjectKey())
                .orElseThrow(() -> new ValidationException(
                        "No upload found for " + attachment.getFilename() + "; PUT the file first"));

        // The declared size was a claim; this is the fact. A mismatch means the
        // client uploaded something other than what it asked permission for.
        if (stored.sizeBytes() > maxBytes) {
            storage.delete(attachment.getObjectKey());
            attachments.delete(attachment);
            throw new ValidationException("Uploaded file exceeds the size limit");
        }

        attachment.markAvailable(stored.sizeBytes());
        return toResponse(attachment);
    }

    public List<AttachmentResponse> list(UUID issueId, UUID actorId) {
        permissions.require(actorId, requireContext(issueId), Permission.BROWSE_PROJECT);
        // PENDING rows are internal bookkeeping -- an upload in flight is not an
        // attachment yet, and showing it would promise a download that 404s.
        return attachments.findByIssueIdAndStatusOrderByCreatedAtAsc(
                        issueId, Attachment.Status.AVAILABLE)
                .stream().map(AttachmentService::toResponse).toList();
    }

    /**
     * A short-lived download URL.
     * <p>
     * Generated per request rather than stored, so revoking access is immediate:
     * someone removed from a project cannot ask for a new URL, and the last one
     * they got expires in minutes.
     */
    public StorageService.PresignedUrl download(UUID attachmentId, UUID actorId) {
        Attachment attachment = load(attachmentId);
        permissions.require(actorId, requireContext(attachment.getIssueId()),
                Permission.BROWSE_PROJECT);

        if (!attachment.isAvailable()) {
            throw new NotFoundException("Attachment", attachmentId);
        }
        return storage.presignDownload(attachment.getObjectKey(), attachment.getFilename(), downloadTtl);
    }

    @Transactional
    public void delete(UUID attachmentId, UUID actorId) {
        Attachment attachment = load(attachmentId);
        permissions.require(actorId, requireContext(attachment.getIssueId()), Permission.EDIT_ISSUE);

        // Row first, object second. If the object delete fails we are left with an
        // orphaned file, which costs storage; the other order leaves a row pointing
        // at nothing, which costs the user a broken download.
        attachments.delete(attachment);
        storage.delete(attachment.getObjectKey());
    }

    /**
     * Called by the sweeper; no permission check, because no user is asking.
     */
    @Transactional
    public int sweepAbandonedUploads(Duration olderThan) {
        List<Attachment> stale = attachments.findByStatusAndCreatedAtBefore(
                Attachment.Status.PENDING, Instant.now().minus(olderThan));

        for (Attachment attachment : stale) {
            // The object may exist if the PUT succeeded and only the completion call
            // was lost, so try to remove it either way.
            storage.delete(attachment.getObjectKey());
            attachments.delete(attachment);
        }
        if (!stale.isEmpty()) {
            log.info("Swept {} abandoned upload(s)", stale.size());
        }
        return stale.size();
    }

    private String validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("A filename is required");
        }
        // Keep only the last path segment. A client that sends "../../etc/passwd"
        // means the file at the end of it, and stripping the separators in place
        // would leave "....etcpasswd" -- harmless, but only by accident, and it
        // preserves an attempt at traversal for whatever reads it next.
        String base = filename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }

        String cleaned = base.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            throw new ValidationException("That filename is not usable");
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    private String validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ValidationException("A content type is required");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();

        // Downloads are served from a storage domain with a forced attachment
        // disposition, but defence in depth: an uploaded HTML or SVG file is a
        // stored cross-site scripting payload waiting for one misconfiguration.
        if (FORBIDDEN_TYPES.contains(normalized)) {
            throw new ValidationException("Files of type " + normalized + " cannot be attached");
        }
        return normalized;
    }

    private IssueContext requireContext(UUID issueId) {
        return contexts.resolve(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
    }

    private Attachment load(UUID attachmentId) {
        return attachments.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment", attachmentId));
    }
}
