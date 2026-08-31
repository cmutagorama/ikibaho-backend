package com.charlie.ikibaho.search.internal.application;

import com.charlie.ikibaho.issue.IssueLookup;
import com.charlie.ikibaho.issue.events.*;
import com.charlie.ikibaho.search.internal.persistence.SearchIndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps the search index in step with the issues.
 * <p>
 * Every handler does the same thing -- re-read the issue and overwrite its
 * document -- because a search index has no interesting per-event logic. What
 * matters is that the index converges on the truth, so the cheapest correct
 * response to "something changed" is to rebuild the row from the source.
 * <p>
 * The index is eventually consistent by design. A comment is searchable a moment
 * after it is posted, not in the same transaction, and that is the trade being
 * made: indexing inline would put a tsvector rebuild on the write path of every
 * edit, and a failure there would roll back the user's actual work.
 */
@Component
public class SearchIndexer {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

    private final IssueLookup issues;
    private final SearchIndexWriter index;

    SearchIndexer(IssueLookup issues, SearchIndexWriter index) {
        this.issues = issues;
        this.index = index;
    }

    @ApplicationModuleListener
    void on(IssueCreated event) {
        reindex(event.issueId());
    }

    @ApplicationModuleListener
    void on(IssueUpdated event) {
        reindex(event.issueId());
    }

    @ApplicationModuleListener
    void on(IssueTransitioned event) {
        reindex(event.issueId());
    }

    @ApplicationModuleListener
    void on(IssueAssigned event) {
        reindex(event.issueId());
    }

    @ApplicationModuleListener
    void on(IssueCommented event) {
        reindex(event.issueId());
    }

    @ApplicationModuleListener
    void on(IssueDeleted event) {
        index.remove(event.issueId());
    }

    /**
     * An issue that has vanished is removed rather than treated as a failure.
     * <p>
     * Events can arrive after a delete -- a retry of an older publication, say --
     * and throwing would leave that publication incomplete forever, retrying a
     * read that can never succeed.
     */
    private void reindex(UUID issueId) {
        issues.findForIndex(issueId).ifPresentOrElse(index::index, () -> {
            log.debug("Issue {} is gone; dropping it from the index", issueId);
            index.remove(issueId);
        });
    }
}
