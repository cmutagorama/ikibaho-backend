package com.charlie.ikibaho.search;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.issue.internal.application.CommentService;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.issue.internal.web.dto.UpdateIssueCommand;
import com.charlie.ikibaho.search.internal.application.SavedFilterService;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The index is filled by async listeners, so every read waits.
 * <p>
 * Same reason as the phase 6 tests, and the same trap: Awaitility polls on its
 * own thread, and SecurityContextHolder is a ThreadLocal, so polling must happen
 * on the test thread or every permission check fails.
 */
public class SearchIntegrationTest extends AbstractIntegrationTest {
    private static final Duration SETTLE = Duration.ofSeconds(10);

    @Autowired
    IssueService issues;
    @Autowired
    CommentService comments;
    @Autowired
    SearchService search;
    @Autowired
    SavedFilterService filters;
    @Autowired
    TestFixtures fixtures;

    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID otherId;
    private UUID outsiderId;
    private UUID taskTypeId;

    private static org.awaitility.core.ConditionFactory settle() {
        return await().atMost(SETTLE).pollInSameThread();
    }

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        otherId = fixtures.user(org, "other@acme.test");
        // In the org but never added to the project. Reusing `otherId` for this made
        // two tests contradict each other: one needs them to see the project, the
        // scope tests need them not to.
        outsiderId = fixtures.user(org, "outsider@acme.test");

        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.addMember(projectId, org, otherId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
    }

    private Issue create(String summary, String description, UUID assigneeId) {
        return issues.create(projectId, adminId, new CreateIssueCommand(
                taskTypeId, summary, description, null, assigneeId, null, null, null, null));
    }

    private SearchResults run(String jql) {
        return search.search(jql, adminId, 50, 0);
    }

    @Test
    void indexesANewIssueAndFindsItByText() {
        Issue issue = create("Login fails on Safari", "The redirect loops forever", null);

        settle().untilAsserted(() ->
                assertThat(run("text ~ \"redirect\"").hits())
                        .extracting(SearchHit::issueKey)
                        .contains(issue.getIssueKey()));
    }

    @Test
    void findsAnIssueByItsKey() {
        Issue issue = create("Anything", null, null);

        settle().untilAsserted(() ->
                assertThat(run("key = " + issue.getIssueKey()).hits()).hasSize(1));
    }

    @Test
    void reindexesWhenTheSummaryChanges() {
        Issue issue = create("Original wording", null, null);

        settle().untilAsserted(() -> assertThat(run("text ~ original").total()).isEqualTo(1));

        issues.update(issue.getId(), adminId,
                new UpdateIssueCommand(issue.getVersion(), "Replacement wording",
                        null, null, null, null, null, null));

        // The old text must stop matching, or search becomes a record of what
        // things used to be called.
        settle().untilAsserted(() -> {
            assertThat(run("text ~ replacement").total()).isEqualTo(1);
            assertThat(run("text ~ original").total()).isZero();
        });
    }

    @Test
    void makesCommentsSearchable() {
        Issue issue = create("Quiet issue", null, null);
        settle().untilAsserted(() -> assertThat(run("key = " + issue.getIssueKey()).total()).isEqualTo(1));

        comments.add(issue.getId(), adminId, "This turned out to be a caching problem");

        settle().untilAsserted(() ->
                assertThat(run("text ~ caching").hits())
                        .extracting(SearchHit::issueKey)
                        .containsExactly(issue.getIssueKey()));
    }

    @Test
    void dropsADeletedIssueFromTheIndex() {
        Issue issue = create("Will be deleted", null, null);
        settle().untilAsserted(() -> assertThat(run("text ~ deleted").total()).isEqualTo(1));

        issues.delete(issue.getId(), adminId);

        settle().untilAsserted(() -> assertThat(run("text ~ deleted").total()).isZero());
    }

    @Test
    void filtersByStructuredFieldsAndCombinesThem() {
        create("Assigned work", null, otherId);
        create("Unassigned work", null, null);

        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(2));

        assertThat(run("assignee IS EMPTY").hits()).hasSize(1);
        assertThat(run("assignee IS NOT EMPTY").hits()).hasSize(1);
        assertThat(run("assignee = " + otherId + " AND type = Task").hits()).hasSize(1);
    }

    @Test
    void resolvesCurrentUserToWhoeverIsAsking() {
        create("Mine", null, adminId);
        create("Theirs", null, otherId);

        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(2));

        assertThat(search.search("assignee = currentUser()", adminId, 50, 0).hits())
                .extracting(SearchHit::summary).containsExactly("Mine");
        // Same query, different person, different answer -- the point of the function.
        assertThat(search.search("assignee = currentUser()", otherId, 50, 0).hits())
                .extracting(SearchHit::summary).containsExactly("Theirs");
    }

    @Test
    void neverReturnsIssuesFromAProjectTheUserCannotBrowse() {
        create("Secret work", null, null);
        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(1));

        // outsiderId is in the org but was never added to the project.
        assertThat(search.search("", outsiderId, 50, 0).hits()).isEmpty();
        // And cannot reach it by naming the project explicitly either.
        assertThat(search.search("project = " + projectId, outsiderId, 50, 0).hits()).isEmpty();
    }

    @Test
    void anOrCannotWidenTheProjectScope() {
        create("Scoped", null, null);
        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(1));

        // The whole user condition is ANDed inside the scope, so the OR cannot
        // reach outside it however it is written.
        assertThat(search.search("project = " + projectId + " OR project = " + UUID.randomUUID(),
                outsiderId, 50, 0).hits()).isEmpty();
    }

    @Test
    void reportsTheTotalSeparatelyFromThePage() {
        for (int i = 0; i < 5; i++) {
            create("Issue number " + i, null, null);
        }
        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(5));

        SearchResults page = search.search("", adminId, 2, 0);

        assertThat(page.hits()).hasSize(2);
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void savesAFilterAndRunsItLater() {
        create("Bug in checkout", null, adminId);
        settle().untilAsserted(() -> assertThat(run("").total()).isEqualTo(1));

        SavedFilterResponse filter = filters.create(org, adminId, "My work",
                "assignee = currentUser()", "Things assigned to me", true);

        assertThat(search.search(filter.jql(), adminId, 50, 0).hits()).hasSize(1);
        assertThat(filters.list(org, adminId)).extracting(SavedFilterResponse::name)
                .contains("My work");
    }

    @Test
    void refusesToSaveAFilterThatCannotParse() {
        // Otherwise it looks fine in the list and fails only when someone clicks it.
        assertThatThrownBy(() -> filters.create(org, adminId, "Broken", "status = = Done", null, false))
                .isInstanceOf(com.charlie.ikibaho.platform.error.ValidationException.class);
    }

    @Test
    void letsOthersSeeASharedFilterButNotEditIt() {
        SavedFilterResponse shared = filters.create(org, adminId, "Team view",
                "status = Done", null, true);

        assertThat(filters.list(org, otherId)).extracting(SavedFilterResponse::name)
                .contains("Team view");

        assertThatThrownBy(() -> filters.update(shared.id(), otherId, "Hijacked", "", null, true))
                .isInstanceOf(com.charlie.ikibaho.platform.error.ForbiddenException.class);
    }

    @Test
    void hidesAPrivateFilterEntirely() {
        SavedFilterResponse privateFilter = filters.create(org, adminId, "Just mine",
                "status = Done", null, false);

        // 404, not 403: whether it exists is not otherId's business.
        assertThatThrownBy(() -> filters.get(privateFilter.id(), otherId))
                .isInstanceOf(com.charlie.ikibaho.platform.error.NotFoundException.class);
    }
}
