package com.charlie.ikibaho.board;


import java.util.UUID;

/**
 * The board module's public surface: two assembled read models.
 *
 * Board owns no issue data. It reads issues through the issue module's published
 * read model and adds only what it does own -- sprint scope and column layout.
 */
public interface BoardService {

    /** The active sprint laid out in columns, or an empty board when none is running. */
    BoardView board(UUID projectId, UUID actorId);

    /** Planned sprints plus uncommitted work, for planning. */
    BacklogView backlog(UUID projectId, UUID actorId);
}
