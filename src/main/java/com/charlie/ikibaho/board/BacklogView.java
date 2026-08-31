package com.charlie.ikibaho.board;

import com.charlie.ikibaho.issue.BoardIssue;

import java.math.BigDecimal;
import java.util.List;

/**
 * The backlog screen: planned sprints with their contents, then everything not
 * yet committed to one.
 *
 * Both halves are in rank order, because planning is a drag between them.
 */
public record BacklogView(
        List<PlannedSprint> sprints,
        List<BoardIssue> backlog,
        int backlogCount,
        BigDecimal backlogStoryPoints
) {
    public record PlannedSprint(
            SprintResponse sprint,
            List<BoardIssue> issues,
            BigDecimal storyPoints
    ) {
    }
}
