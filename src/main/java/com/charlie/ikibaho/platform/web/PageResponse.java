package com.charlie.ikibaho.platform.web;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(List.of(), null, false);
    }

    /**
     * A full page implies there may be more; a short page is definitively the last,
     * so the cursor is suppressed rather than handing back one that yields nothing.
     */
    public static <T> PageResponse<T> of(List<T> items, int limit, String nextCursor) {
        boolean hasMore = items.size() == limit;
        return new PageResponse<>(items, hasMore ? nextCursor : null, hasMore);
    }
}
