package com.charlie.ikibaho.search;

import java.util.List;

/**
 * A page of hits plus the total.
 * <p>
 * The total is separate from the page because a search box needs to say "241
 * results" while showing twenty.
 */
public record SearchResults(List<SearchHit> hits, long total, int offset, int limit) {
    public boolean hasMore() {
        return offset + hits.size() < total;
    }
}
