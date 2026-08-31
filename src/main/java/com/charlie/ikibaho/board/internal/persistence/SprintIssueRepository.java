package com.charlie.ikibaho.board.internal.persistence;

import com.charlie.ikibaho.board.internal.domain.SprintIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SprintIssueRepository extends JpaRepository<SprintIssue, SprintIssue.Id> {

    List<SprintIssue> findByIdSprintId(UUID sprintId);

    @Query("SELECT si.id.issueId FROM SprintIssue si WHERE si.id.sprintId = :sprintId")
    List<UUID> findIssueIds(UUID sprintId);

    /**
     * Every issue already committed to an open sprint in this project.
     *
     * The backlog is defined by exclusion -- anything not in an open sprint --
     * so this is the set the backlog query subtracts.
     */
    @Query("""
            SELECT si.id.issueId FROM SprintIssue si
            WHERE si.id.sprintId IN (
                SELECT s.id FROM Sprint s
                WHERE s.projectId = :projectId AND s.state <> com.charlie.ikibaho.board.internal.domain.SprintState.COMPLETED
            )
            """)
    List<UUID> findIssueIdsInOpenSprints(UUID projectId);

    /**
     * Bulk delete, deliberately without {@code clearAutomatically}.
     *
     * Clearing would evict the entire persistence context, not just these rows --
     * detaching the Sprint the caller is holding, so a subsequent state change on
     * it would be silently dropped at commit. That cost a real bug: completing a
     * sprint appeared to work and never persisted.
     *
     * Nothing here holds SprintIssue instances across the call, so there is no
     * stale state to clear.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SprintIssue si WHERE si.id.sprintId = :sprintId AND si.id.issueId IN :issueIds")
    int removeAll(UUID sprintId, Collection<UUID> issueIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SprintIssue si WHERE si.id.sprintId = :sprintId")
    int removeAllInSprint(UUID sprintId);
}
