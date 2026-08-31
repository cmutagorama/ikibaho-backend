package com.charlie.ikibaho.activity.internal.application;

import com.charlie.ikibaho.activity.internal.domain.HistoryKind;
import com.charlie.ikibaho.activity.internal.domain.IssueHistory;
import com.charlie.ikibaho.activity.internal.persistence.IssueHistoryRepository;
import com.charlie.ikibaho.activity.internal.web.dto.ActivityEntry;
import com.charlie.ikibaho.identity.UserService;
import com.charlie.ikibaho.identity.UserSummary;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.project.IssueContext;
import com.charlie.ikibaho.project.IssueContextResolver;
import com.charlie.ikibaho.project.PermissionService;
import com.charlie.ikibaho.project.Permission;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads of the history the recorder writes.
 *
 * Activity is a read-only module over other modules' facts, so everything it
 * needs from elsewhere comes through a published interface: PermissionService
 * to decide who may look, UserService to put names on ids.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {
    private final IssueHistoryRepository history;
    private final PermissionService permissions;
    private final IssueContextResolver contexts;
    private final UserService users;

    ActivityService(IssueHistoryRepository history, PermissionService permissions,
                    IssueContextResolver contexts, UserService users) {
        this.history = history;
        this.permissions = permissions;
        this.contexts = contexts;
        this.users = users;
    }

    public List<ActivityEntry> forIssue(UUID issueId, UUID actorId, int limit) {
        IssueContext ctx = contexts.resolve(issueId)
                .orElseThrow(() -> new NotFoundException("Issue", issueId));
        permissions.require(actorId, ctx, Permission.BROWSE_PROJECT);

        return assemble(history.findByIssueIdOrderByOccurredAtDesc(
                issueId, PageRequest.ofSize(limit)));
    }

    public List<ActivityEntry> forProject(UUID projectId, UUID actorId, int limit) {
        permissions.require(actorId, projectId, Permission.BROWSE_PROJECT);

        return assemble(history.findByProjectIdOrderByOccurredAtDesc(
                projectId, PageRequest.ofSize(limit)));
    }

    /**
     * Resolves every referenced person in one batch.
     *
     * A per-row lookup here would be N+1 over the whole feed, and the feed is the
     * one screen guaranteed to show many different people at once.
     */
    private List<ActivityEntry> assemble(List<IssueHistory> lines) {
        Set<UUID> userIds = new HashSet<>();
        for (IssueHistory line : lines) {
            if (line.getActorId() != null) userIds.add(line.getActorId());
            if (line.getKind() == HistoryKind.ASSIGNED) {
                addIfUuid(userIds, line.getOldValue());
                addIfUuid(userIds, line.getNewValue());
            }
        }

        Map<UUID, UserSummary> people = userIds.isEmpty()
                ? Map.of()
                : users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));

        List<ActivityEntry> out = new ArrayList<>(lines.size());
        for (IssueHistory line : lines) {
            boolean assignment = line.getKind() == HistoryKind.ASSIGNED;
            out.add(new ActivityEntry(
                    line.getId(),
                    line.getIssueId(),
                    line.getIssueKey(),
                    line.getKind().name(),
                    line.getField(),
                    assignment ? displayName(people, line.getOldValue()) : line.getOldValue(),
                    assignment ? displayName(people, line.getNewValue()) : line.getNewValue(),
                    line.getReferenceId(),
                    actorOf(people, line.getActorId()),
                    line.getOccurredAt()));
        }
        return out;
    }

    private static ActivityEntry.Actor actorOf(Map<UUID, UserSummary> people, UUID id) {
        if (id == null) return null;
        UserSummary user = people.get(id);
        // A deleted user still has history; show the id rather than dropping the line.
        return user == null
                ? new ActivityEntry.Actor(id, "Unknown user", null)
                : new ActivityEntry.Actor(user.id(), user.displayName(), user.avatarUrl());
    }

    /** Null stays null -- for an assignment that is "Unassigned", not a missing name. */
    private static String displayName(Map<UUID, UserSummary> people, String rawId) {
        if (rawId == null) return null;
        UUID id = parseUuid(rawId);
        if (id == null) return rawId;
        UserSummary user = people.get(id);
        return user == null ? "Unknown user" : user.displayName();
    }

    private static void addIfUuid(Set<UUID> target, String raw) {
        UUID id = parseUuid(raw);
        if (id != null) target.add(id);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
