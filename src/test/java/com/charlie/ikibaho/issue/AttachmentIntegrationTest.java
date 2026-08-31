package com.charlie.ikibaho.issue;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.issue.internal.application.AttachmentService;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.web.dto.UploadTicket;
import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.support.InMemoryStorage;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Storage is substituted. Presigning is offline arithmetic and needs no bucket,
 * but HEAD does -- and standing up MinIO to prove that a two-step upload has two
 * steps is not worth the runtime.
 */
@Import(InMemoryStorage.class)
public class AttachmentIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    IssueService issues;
    @Autowired
    AttachmentService attachments;
    @Autowired
    InMemoryStorage.Fake storage;
    @Autowired
    TestFixtures fixtures;

    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID viewerId;
    private UUID taskTypeId;
    private Issue issue;

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        viewerId = fixtures.user(org, "viewer@acme.test");
        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.addViewer(projectId, org, viewerId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
        storage.reset();

        issue = issues.create(projectId, adminId, new com.charlie.ikibaho.issue.internal.web.dto
                .CreateIssueCommand(taskTypeId, "Has attachments", null, null, null, null, null, null, null));
    }

    private UploadTicket request(String filename, String type, long size) {
        return attachments.requestUpload(issue.getId(), adminId, filename, type, size);
    }

    @Test
    void issuesAnUploadUrlAndHidesTheAttachmentUntilItCompletes() {
        UploadTicket ticket = request("design.png", "image/png", 1024);

        assertThat(ticket.uploadUrl()).isNotBlank();
        // Listing a file that has not arrived would promise a download that 404s.
        assertThat(attachments.list(issue.getId(), adminId)).isEmpty();
    }

    @Test
    void publishesTheAttachmentOnceTheBytesLand() {
        UploadTicket ticket = request("design.png", "image/png", 1024);
        storage.put(storage.lastKey(), 1024, "image/png");

        attachments.completeUpload(ticket.attachmentId(), adminId);

        assertThat(attachments.list(issue.getId(), adminId))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.filename()).isEqualTo("design.png");
                    assertThat(a.sizeBytes()).isEqualTo(1024);
                });
    }

    @Test
    void refusesToCompleteAnUploadThatNeverHappened() {
        UploadTicket ticket = request("ghost.png", "image/png", 1024);

        assertThatThrownBy(() -> attachments.completeUpload(ticket.attachmentId(), adminId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PUT the file first");
    }

    @Test
    void recordsTheRealSizeRatherThanTheClaimedOne() {
        UploadTicket ticket = request("small.png", "image/png", 10);
        // The client claimed 10 bytes and uploaded 5000.
        storage.put(storage.lastKey(), 5000, "image/png");

        attachments.completeUpload(ticket.attachmentId(), adminId);

        assertThat(attachments.list(issue.getId(), adminId))
                .singleElement()
                .satisfies(a -> assertThat(a.sizeBytes()).isEqualTo(5000));
    }

    @Test
    void refusesAnOversizedDeclaredUploadBeforeIssuingAUrl() {
        assertThatThrownBy(() -> request("huge.zip", "application/zip", 999_999_999L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("bytes");
    }

    @Test
    void refusesFileTypesABrowserWouldExecute() {
        assertThatThrownBy(() -> request("payload.html", "text/html", 100))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> request("payload.svg", "image/svg+xml", 100))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void stripsPathSeparatorsFromTheFilename() {
        UploadTicket ticket = request("../../etc/passwd", "text/plain", 100);
        storage.put(storage.lastKey(), 100, "text/plain");
        attachments.completeUpload(ticket.attachmentId(), adminId);

        assertThat(attachments.list(issue.getId(), adminId))
                .singleElement()
                // The basename, not the path with its separators rubbed out.
                .satisfies(a -> assertThat(a.filename()).isEqualTo("passwd"));
    }

    @Test
    void givesEachUploadItsOwnObjectKey() {
        request("same-name.png", "image/png", 100);
        String first = storage.lastKey();
        request("same-name.png", "image/png", 100);

        // Two people uploading "screenshot.png" must not overwrite each other.
        assertThat(storage.lastKey()).isNotEqualTo(first);
    }

    @Test
    void letsAViewerDownloadButNotUpload() {
        UploadTicket ticket = request("readable.png", "image/png", 100);
        storage.put(storage.lastKey(), 100, "image/png");
        attachments.completeUpload(ticket.attachmentId(), adminId);

        assertThat(attachments.download(ticket.attachmentId(), viewerId).url()).isNotBlank();

        assertThatThrownBy(() -> attachments.requestUpload(
                issue.getId(), viewerId, "nope.png", "image/png", 100))
                .isInstanceOf(com.charlie.ikibaho.platform.error.ForbiddenException.class);
    }

    @Test
    void removesTheObjectWhenTheAttachmentIsDeleted() {
        UploadTicket ticket = request("temporary.png", "image/png", 100);
        String key = storage.lastKey();
        storage.put(key, 100, "image/png");
        attachments.completeUpload(ticket.attachmentId(), adminId);

        attachments.delete(ticket.attachmentId(), adminId);

        assertThat(storage.head(key)).isEmpty();
        assertThat(attachments.list(issue.getId(), adminId)).isEmpty();
    }

    @Test
    void sweepsUploadsThatWereRequestedAndNeverCompleted() {
        request("abandoned.png", "image/png", 100);

        // Zero means "everything older than now", so the sweep covers it.
        assertThat(attachments.sweepAbandonedUploads(Duration.ZERO)).isEqualTo(1);
        assertThat(attachments.list(issue.getId(), adminId)).isEmpty();
    }
}
