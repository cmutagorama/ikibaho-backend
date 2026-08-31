package com.charlie.ikibaho.issue.internal.domain;

import com.charlie.ikibaho.platform.domain.BaseEntity;
import com.charlie.ikibaho.platform.error.ConflictException;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * An uploaded file's metadata. The bytes are in object storage.
 * <p>
 * A row exists in PENDING state from the moment a presigned URL is issued, which
 * is before any bytes have arrived. That is what makes the upload verifiable:
 * without a record of what was promised there is no way to later ask storage
 * whether it turned up, or whether its size matches what was claimed.
 */
@Entity
@Table(name = "attachment")

public class Attachment extends BaseEntity {
    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    protected Attachment() {
    }

    public Attachment(UUID issueId, UUID projectId, String filename, String contentType,
                      long sizeBytes, String objectKey, UUID uploadedBy) {
        this.issueId = issueId;
        this.projectId = projectId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.uploadedBy = uploadedBy;
        this.status = Status.PENDING;
    }

    /**
     * Confirm the upload, recording the size storage actually reports.
     * <p>
     * The declared size is replaced rather than trusted: it came from the client,
     * and the whole point of asking storage is to find out what really landed.
     */
    public void markAvailable(long actualSizeBytes) {
        if (status == Status.AVAILABLE) {
            throw new ConflictException("Attachment " + filename + " is already uploaded");
        }
        this.sizeBytes = actualSizeBytes;
        this.status = Status.AVAILABLE;
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Status getStatus() {
        return status;
    }

    public enum Status {PENDING, AVAILABLE}
}
