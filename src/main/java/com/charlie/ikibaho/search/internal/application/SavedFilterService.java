package com.charlie.ikibaho.search.internal.application;

import com.charlie.ikibaho.platform.error.ConflictException;
import com.charlie.ikibaho.platform.error.NotFoundException;
import com.charlie.ikibaho.search.SavedFilterResponse;
import com.charlie.ikibaho.search.SearchService;
import com.charlie.ikibaho.search.internal.domain.SavedFilter;
import com.charlie.ikibaho.search.internal.persistence.SavedFilterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SavedFilterService {
    private final SavedFilterRepository filters;
    private final SearchService search;

    SavedFilterService(SavedFilterRepository filters, SearchService search) {
        this.filters = filters;
        this.search = search;
    }

    private static SavedFilterResponse toResponse(SavedFilter f, UUID viewerId) {
        return new SavedFilterResponse(f.getId(), f.getName(), f.getJql(), f.getDescription(),
                f.isShared(), f.getOwnerId(), f.getOwnerId().equals(viewerId));
    }

    @Transactional
    public SavedFilterResponse create(UUID organizationId, UUID ownerId, String name, String jql,
                                      String description, boolean shared) {
        // Validated before saving. A filter that cannot parse is a trap: it looks
        // fine in the list and fails only when someone clicks it.
        search.validate(jql);

        if (filters.existsByOwnerIdAndName(ownerId, name)) {
            throw new ConflictException("You already have a filter called '" + name + "'");
        }
        SavedFilter filter = filters.save(
                new SavedFilter(organizationId, ownerId, name.trim(), jql, description, shared));
        return toResponse(filter, ownerId);
    }

    public List<SavedFilterResponse> list(UUID organizationId, UUID userId) {
        return filters.visibleTo(organizationId, userId).stream()
                .map(f -> toResponse(f, userId))
                .toList();
    }

    public SavedFilterResponse get(UUID filterId, UUID userId) {
        return toResponse(requireVisible(filterId, userId), userId);
    }

    @Transactional
    public SavedFilterResponse update(UUID filterId, UUID userId, String name, String jql,
                                      String description, boolean shared) {
        search.validate(jql);

        SavedFilter filter = requireVisible(filterId, userId);
        filter.requireOwnedBy(userId);
        filter.update(name.trim(), jql, description, shared);
        return toResponse(filter, userId);
    }

    @Transactional
    public void delete(UUID filterId, UUID userId) {
        SavedFilter filter = requireVisible(filterId, userId);
        filter.requireOwnedBy(userId);
        filters.delete(filter);
    }

    /**
     * 404 rather than 403 for a filter you cannot see: whether someone else's
     * private filter exists is not information you are entitled to.
     */
    private SavedFilter requireVisible(UUID filterId, UUID userId) {
        SavedFilter filter = filters.findById(filterId)
                .orElseThrow(() -> new NotFoundException("Filter", filterId));
        if (!filter.isVisibleTo(userId)) {
            throw new NotFoundException("Filter", filterId);
        }
        return filter;
    }
}
