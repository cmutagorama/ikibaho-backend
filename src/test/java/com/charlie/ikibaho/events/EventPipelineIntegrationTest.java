package com.charlie.ikibaho.events;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.activity.internal.application.ActivityService;
import com.charlie.ikibaho.activity.internal.web.dto.ActivityEntry;
import com.charlie.ikibaho.issue.events.IssueCreated;
import com.charlie.ikibaho.issue.internal.application.CommentService;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.domain.Issue;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.notification.internal.application.NotificationService;
import com.charlie.ikibaho.notification.internal.web.dto.NotificationResponse;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The phase 6 pipeline end to end: a domain call publishes, the registry stores,
 * a listener in another module reacts.
 * <p>
 * Everything here waits rather than asserting immediately. Listeners are
 * {@code @ApplicationModuleListener} -- async, after commit -- so an immediate
 * assertion would be a race that passes on a fast machine and fails in CI.
 */
class EventPipelineIntegrationTest extends AbstractIntegrationTest {
    private static final Duration SETTLE = Duration.ofSeconds(10);
    @Autowired
    IssueService issues;
    @Autowired
    CommentService comments;
    @Autowired
    ActivityService activity;
    @Autowired
    NotificationService notifications;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    TransactionTemplate transactions;
    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID memberId;
    private UUID taskTypeId;

    /**
     * Awaitility polls on its own thread by default, and SecurityContextHolder is
     * a ThreadLocal -- so an assertion that calls a permission-checked service
     * would fail with "No authenticated user" no matter how long it waited.
     * Polling in the test thread keeps the authentication from @BeforeEach.
     */
    private static org.awaitility.core.ConditionFactory settle() {
        return await().atMost(SETTLE).pollInSameThread();
    }

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        memberId = fixtures.user(org, "member@acme.test");
        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.addMember(projectId, org, memberId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
    }

    @Test
    void creatingAnIssueWritesAHistoryLineInAnotherModule() {
        Issue issue = issues.create(projectId, adminId, cmd("Wire up events", null));

        settle().untilAsserted(() -> {
            List<ActivityEntry> entries = activity.forIssue(issue.getId(), adminId, 10);
            assertThat(entries).hasSize(1);
            assertThat(entries.getFirst().kind()).isEqualTo("CREATED");
            assertThat(entries.getFirst().newValue()).isEqualTo("Wire up events");
            assertThat(entries.getFirst().actor().id()).isEqualTo(adminId);
        });
    }

    @Test
    void thePublicationIsRecordedInTheOutboxAndThenCompleted() {
        issues.create(projectId, adminId, cmd("Outbox proof", null));

        // Every listener that saw the event must have completed it. An incomplete
        // row left behind is the signature of a handler that threw.
        settle().untilAsserted(() -> {
            Long incomplete = jdbc.sql("""
                            SELECT count(*) FROM event_publication
                            WHERE completion_date IS NULL
                            """)
                    .query(Long.class).single();
            assertThat(incomplete).isZero();
        });

        Long total = jdbc.sql("SELECT count(*) FROM event_publication")
                .query(Long.class).single();
        assertThat(total).isPositive();
    }

    @Test
    void assigningNotifiesTheAssigneeButNotTheAssigner() {
        Issue issue = issues.create(projectId, adminId, cmd("Needs an owner", null));
        issues.assign(issue.getId(), adminId, memberId);

        settle().untilAsserted(() -> {
            List<NotificationResponse> inbox = notifications.inbox(memberId, false, 10);
            assertThat(inbox).hasSize(1);
            assertThat(inbox.getFirst().kind()).isEqualTo("ISSUE_ASSIGNED");
            assertThat(inbox.getFirst().title()).contains(issue.getIssueKey());
        });

        // The person who did the assigning hears nothing about their own action.
        assertThat(notifications.inbox(adminId, false, 10)).isEmpty();
    }

    @Test
    void commentingNotifiesTheAssigneeButNeverTheAuthor() {
        Issue issue = issues.create(projectId, adminId, cmd("Discuss me", memberId));

        // memberId is both assignee and author here, so only the reporter (admin)
        // should hear about it.
        fixtures.authenticateAs(memberId, org);
        comments.add(issue.getId(), memberId, "Looks good to me");

        settle().untilAsserted(() -> {
            List<NotificationResponse> adminInbox = notifications.inbox(adminId, false, 10);
            assertThat(adminInbox)
                    .extracting(NotificationResponse::kind)
                    .contains("ISSUE_COMMENTED");
        });

        assertThat(notifications.inbox(memberId, false, 10))
                .extracting(NotificationResponse::kind)
                .doesNotContain("ISSUE_COMMENTED");
    }

    @Test
    void unreadCountDropsWhenEverythingIsMarkedRead() {
        Issue issue = issues.create(projectId, adminId, cmd("Count me", null));
        issues.assign(issue.getId(), adminId, memberId);

        settle()
                .untilAsserted(() -> assertThat(notifications.unreadCount(memberId)).isEqualTo(1));

        assertThat(notifications.markAllRead(memberId)).isEqualTo(1);
        assertThat(notifications.unreadCount(memberId)).isZero();

        // Idempotent: a second call has nothing left to update.
        assertThat(notifications.markAllRead(memberId)).isZero();
    }

    /**
     * The dedupe guard.
     * <p>
     * The registry is at-least-once, so the recorder must survive seeing the same
     * event twice. Rather than kill the JVM mid-handler, this delivers one
     * identical event a second time -- two publications, same payload -- which is
     * what a redelivery looks like to the listener.
     * <p>
     * An earlier version reset completion_date by SQL and called
     * resubmitIncompletePublications. That redelivered nothing, so it passed even
     * with the dedupe key deliberately broken. This version was checked the other
     * way round: break the key, and it fails.
     */
    @Test
    void theSameEventDeliveredTwiceRecordsOneHistoryLine() {
        Issue issue = issues.create(projectId, adminId, cmd("Replay me", null));

        settle().untilAsserted(() ->
                assertThat(activity.forIssue(issue.getId(), adminId, 10)).hasSize(1));

        // Identical in every component, including occurredAt: the dedupe key is
        // derived from the event, so this is indistinguishable from a redelivery.
        IssueCreated replay = new IssueCreated(issue.getId(), projectId, org,
                issue.getIssueKey(), issue.getSummary(), adminId, null,
                recordedTimestampFor(issue.getId()));

        transactions.executeWithoutResult(status -> publisher.publishEvent(replay));

        settle().untilAsserted(() -> {
            Long incomplete = jdbc.sql("""
                            SELECT count(*) FROM event_publication
                            WHERE completion_date IS NULL
                            """)
                    .query(Long.class).single();
            assertThat(incomplete).isZero();
        });

        Long lines = jdbc.sql("SELECT count(*) FROM issue_history WHERE issue_id = ?")
                .param(issue.getId())
                .query(Long.class).single();
        assertThat(lines).isEqualTo(1);
    }

    /** The occurredAt already stored, so the replay's derived key matches. */
    private Instant recordedTimestampFor(UUID issueId) {
        return jdbc.sql("SELECT occurred_at FROM issue_history WHERE issue_id = ?")
                .param(issueId)
                .query(Instant.class).single();
    }

    private CreateIssueCommand cmd(String summary, UUID assigneeId) {
        return new CreateIssueCommand(taskTypeId, summary, null, null, assigneeId,
                null, null, null, null);
    }
}
