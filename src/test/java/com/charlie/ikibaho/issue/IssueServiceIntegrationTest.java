package com.charlie.ikibaho.issue;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.issue.internal.web.dto.UpdateIssueCommand;
import com.charlie.ikibaho.platform.error.ForbiddenException;
import com.charlie.ikibaho.project.Permission;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IssueServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    IssueService issues;
    @Autowired
    TestFixtures fixtures;

    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID viewerId;
    private UUID taskTypeId;

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        viewerId = fixtures.user(org, "viewer@acme.test");
        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.addViewer(projectId, org, viewerId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
    }

    @Test
    void assignsSequentialKeysPerProject() {
        Issue first = issues.create(projectId, adminId, cmd("First"));
        Issue second = issues.create(projectId, adminId, cmd("Second"));

        assertThat(first.getIssueKey()).isEqualTo("ACME-1");
        assertThat(second.getIssueKey()).isEqualTo("ACME-2");
    }

    @Test
    void rejectsStaleVersion() {
        Issue issue = issues.create(projectId, adminId, cmd("Original"));

        assertThatThrownBy(() -> issues.update(issue.getId(), adminId,
                update(issue.getVersion() + 99, "Renamed")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void viewerCannotEditSomeoneElsesIssue() {
        Issue issue = issues.create(projectId, adminId, cmd("Admin's issue"));
        fixtures.authenticateAs(viewerId, org);

        // Read the real version so this fails on permissions, not on a stale lock.
        assertThatThrownBy(() -> issues.update(issue.getId(), viewerId,
                update(issue.getVersion(), "Hijacked")))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * Viewers get CREATE_ISSUE but NOT EDIT_ISSUE, so a successful edit here can
     * only come from the REPORTER dynamic grant.
     */
    @Test
    void viewerCanEditIssueTheyReported() {
        fixtures.grantToRole(projectId, org, "Viewers", Permission.CREATE_ISSUE);
        fixtures.authenticateAs(viewerId, org);

        Issue own = issues.create(projectId, viewerId, cmd("Viewer's own"));
        Issue updated = issues.update(own.getId(), viewerId, update(own.getVersion(), "Edited"));

        assertThat(updated.getSummary()).isEqualTo("Edited");
    }

    private CreateIssueCommand cmd(String summary) {
        return new CreateIssueCommand(taskTypeId, summary, null, null, null, null, null, null, null);
    }

    private UpdateIssueCommand update(long version, String summary) {
        return new UpdateIssueCommand(version, summary, null, null, null, null, null, null);
    }
}
