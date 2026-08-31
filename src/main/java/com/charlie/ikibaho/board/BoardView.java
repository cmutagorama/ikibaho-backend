package com.charlie.ikibaho.board;

import com.charlie.ikibaho.issue.BoardIssue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A board: the active sprint's issues, laid out in status columns.
 *
 * Columns are returned even when empty. A column that vanished because nothing
 * was in it would make the board's shape change under the user as they drag the
 * last card out of it -- and leave nowhere to drop it back.
 */
public record BoardView(
        UUID projectId,
        SprintResponse sprint,
        List<Column> columns,
        int totalIssues,
        BigDecimal totalStoryPoints
) {
    public record Column(
            UUID statusId,
            String name,
            String category,
            List<BoardIssue> issues,
            int count,
            BigDecimal storyPoints
    ) {
    }
}
