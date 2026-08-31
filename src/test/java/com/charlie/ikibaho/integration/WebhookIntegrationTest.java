package com.charlie.ikibaho.integration;

import com.charlie.ikibaho.AbstractIntegrationTest;
import com.charlie.ikibaho.integration.internal.application.WebhookService;
import com.charlie.ikibaho.integration.internal.delivery.RecordingWebhookSender;
import com.charlie.ikibaho.integration.internal.delivery.WebhookDeliveryJob;
import com.charlie.ikibaho.integration.internal.domain.WebhookEvent;
import com.charlie.ikibaho.issue.internal.application.IssueService;
import com.charlie.ikibaho.issue.internal.web.dto.CreateIssueCommand;
import com.charlie.ikibaho.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Import(RecordingWebhookSender.class)
class WebhookIntegrationTest extends AbstractIntegrationTest {
    private static final Duration SETTLE = Duration.ofSeconds(10);

    @Autowired
    IssueService issues;
    @Autowired
    WebhookService webhooks;
    @Autowired
    WebhookDeliveryJob job;
    @Autowired
    RecordingWebhookSender.Recorder sender;
    @Autowired
    TestFixtures fixtures;

    private UUID org;
    private UUID projectId;
    private UUID adminId;
    private UUID taskTypeId;

    private static org.awaitility.core.ConditionFactory settle() {
        return await().atMost(SETTLE).pollInSameThread();
    }

    @BeforeEach
    void setUp() {
        org = fixtures.organization("Acme");
        adminId = fixtures.user(org, "admin@acme.test");
        projectId = fixtures.project(org, "ACME", adminId);
        fixtures.authenticateAs(adminId, org);
        taskTypeId = fixtures.ensureTaskType(org);
        sender.reset();
    }

    private void createIssue(String summary) {
        issues.create(projectId, adminId, new CreateIssueCommand(
                taskTypeId, summary, null, null, null, null, null, null, null));
    }

    @Test
    void queuesADeliveryForEachSubscribedWebhook() {
        webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));

        createIssue("Something happened");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(sender.sent()).hasSize(1);
            assertThat(sender.sent().getFirst().eventType()).isEqualTo("issue.created");
        });
    }

    @Test
    void doesNotQueueEventsAWebhookDidNotSubscribeTo() {
        webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.PROJECT_CREATED));

        createIssue("Not interesting to this subscriber");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(sender.sent()).isEmpty();
        });
    }

    @Test
    void signsEveryDeliveryWithTheWebhooksOwnSecret() {
        WebhookCreated created = webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));

        createIssue("Signed please");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(sender.sent()).hasSize(1);
            assertThat(sender.sent().getFirst().secret()).isEqualTo(created.secret());
        });
    }

    @Test
    void retriesAFailedDeliveryWithBackoffRatherThanImmediately() {
        webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));
        sender.failWith(500);

        createIssue("Will fail");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(sender.sent()).hasSize(1);
        });

        // Immediately due again would be a hot loop against a struggling receiver.
        int before = sender.sent().size();
        job.drainNow();
        assertThat(sender.sent()).hasSize(before);
    }

    @Test
    void marksADeliveryDeliveredOnSuccess() {
        WebhookCreated created = webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));

        createIssue("Will succeed");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(webhooks.recentDeliveries(created.webhook().id(), org, 10))
                    .singleElement()
                    .satisfies(d -> assertThat(d.state()).isEqualTo("DELIVERED"));
        });
    }

    @Test
    void skipsWebhooksThatHaveBeenDeactivated() {
        WebhookCreated created = webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));
        webhooks.update(created.webhook().id(), org, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED), false);

        createIssue("Nobody is listening");

        settle().untilAsserted(() -> {
            job.drainNow();
            assertThat(sender.sent()).isEmpty();
        });
    }

    @Test
    void neverReturnsTheSecretAfterCreation() {
        WebhookCreated created = webhooks.create(org, adminId, "https://example.test/hook", null,
                Set.of(WebhookEvent.ISSUE_CREATED));

        assertThat(created.secret()).isNotBlank();
        // The read model has no field for it at all -- not a blanked-out one.
        assertThat(webhooks.get(created.webhook().id(), org).toString())
                .doesNotContain(created.secret());
    }

    @Test
    void hidesAnotherOrganizationsWebhook() {
        UUID otherOrg = fixtures.organization("Other");
        UUID otherAdmin = fixtures.user(otherOrg, "admin@other.test");
        WebhookCreated theirs = webhooks.create(otherOrg, otherAdmin, "https://other.test/hook",
                null, Set.of(WebhookEvent.ISSUE_CREATED));

        assertThatThrownBy(() -> webhooks.get(theirs.webhook().id(), org))
                .isInstanceOf(com.charlie.ikibaho.platform.error.NotFoundException.class);
    }

    @Test
    void rejectsAnUnknownEventTypeAtRegistration() {
        assertThatThrownBy(() -> webhooks.create(org, adminId, "https://example.test/hook",
                null, Set.of("issue.exploded")))
                .isInstanceOf(com.charlie.ikibaho.platform.error.ValidationException.class)
                .hasMessageContaining("Unknown event type");
    }

    @Test
    void rejectsANonHttpUrl() {
        assertThatThrownBy(() -> webhooks.create(org, adminId, "file:///etc/passwd",
                null, Set.of(WebhookEvent.ISSUE_CREATED)))
                .isInstanceOf(com.charlie.ikibaho.platform.error.ValidationException.class);
    }
}
