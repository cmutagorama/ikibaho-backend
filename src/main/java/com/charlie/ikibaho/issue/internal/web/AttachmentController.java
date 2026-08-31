package com.charlie.ikibaho.issue.internal.web;

import com.charlie.ikibaho.issue.internal.application.AttachmentService;
import com.charlie.ikibaho.issue.internal.web.dto.AttachmentResponse;
import com.charlie.ikibaho.issue.internal.web.dto.UploadTicket;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import com.charlie.ikibaho.storage.StorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
@Validated
class AttachmentController {
    private final AttachmentService attachments;
    private final CurrentUser currentUser;

    AttachmentController(AttachmentService attachments, CurrentUser currentUser) {
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    /**
     * Step one: ask for permission to upload, and get somewhere to put it.
     */
    @PostMapping("/issues/{issueId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    UploadTicket requestUpload(@PathVariable UUID issueId,
                               @Valid @RequestBody UploadRequest req) {
        return attachments.requestUpload(issueId, currentUser.requireId(),
                req.filename(), req.contentType(), req.sizeBytes());
    }

    /**
     * Step three: tell the server the bytes landed, so it can verify and publish.
     */
    @PostMapping("/attachments/{attachmentId}/complete")
    AttachmentResponse complete(@PathVariable UUID attachmentId) {
        return attachments.completeUpload(attachmentId, currentUser.requireId());
    }

    @GetMapping("/issues/{issueId}/attachments")
    List<AttachmentResponse> list(@PathVariable UUID issueId) {
        return attachments.list(issueId, currentUser.requireId());
    }

    /**
     * Returns a URL rather than the bytes.
     * <p>
     * A 302 would be friendlier to a browser but hostile to an API client that
     * follows redirects with its Authorization header still attached -- which
     * would hand the storage provider a credential for this API.
     */
    @GetMapping("/attachments/{attachmentId}/download")
    Map<String, Object> download(@PathVariable UUID attachmentId) {
        StorageService.PresignedUrl url = attachments.download(attachmentId, currentUser.requireId());
        return Map.of("url", url.url(), "expiresAt", url.expiresAt());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID attachmentId) {
        attachments.delete(attachmentId, currentUser.requireId());
    }

    record UploadRequest(@NotBlank String filename, @NotBlank String contentType,
                         @Positive long sizeBytes) {
    }
}
