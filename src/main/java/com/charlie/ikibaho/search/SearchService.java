package com.charlie.ikibaho.search;

import java.util.UUID;

/**
 * Search over issues, by JQL.
 * <p>
 * Every method takes the acting user and scopes results to what they may browse.
 * There is no unscoped variant on purpose -- one would eventually be called by
 * something that forgot to filter afterwards.
 */
public interface SearchService {
    /**
     * @param jql    the query; blank means "everything I can see"
     * @param limit  page size
     * @param offset rows to skip
     */
    SearchResults search(String jql, UUID actorId, int limit, int offset);

    /**
     * Validates a query without running it, for an editor that checks as you type.
     */
    void validate(String jql);
}
