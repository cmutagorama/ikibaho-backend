package com.charlie.ikibaho.search.internal.web;

import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import com.charlie.ikibaho.search.SavedFilterResponse;
import com.charlie.ikibaho.search.SearchResults;
import com.charlie.ikibaho.search.SearchService;
import com.charlie.ikibaho.search.internal.application.SavedFilterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
@Validated
public class SearchController {
    private final SearchService search;
    private final SavedFilterService filters;
    private final CurrentUser currentUser;

    SearchController(SearchService search, SavedFilterService filters, CurrentUser currentUser) {
        this.search = search;
        this.filters = filters;
        this.currentUser = currentUser;
    }

    /**
     * GET rather than POST: a search is a read, and the query belongs in a URL
     * that can be bookmarked and shared.
     */
    @GetMapping("/search")
    SearchResults search(@RequestParam(required = false) String jql,
                         @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
                         @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return search.search(jql, currentUser.requireId(), limit, offset);
    }

    /**
     * For an editor that validates as the user types, without running anything.
     */
    @PostMapping("/search/validate")
    Map<String, Object> validate(@RequestBody ValidateRequest req) {
        search.validate(req.jql());
        return Map.of("valid", true);
    }

    @GetMapping("/filters")
    List<SavedFilterResponse> listFilters() {
        return filters.list(currentUser.requireOrganizationId(), currentUser.requireId());
    }

    @GetMapping("/filters/{filterId}")
    SavedFilterResponse getFilter(@PathVariable UUID filterId) {
        return filters.get(filterId, currentUser.requireId());
    }

    @PostMapping("/filters")
    @ResponseStatus(HttpStatus.CREATED)
    SavedFilterResponse createFilter(@Valid @RequestBody FilterRequest req) {
        return filters.create(currentUser.requireOrganizationId(), currentUser.requireId(),
                req.name(), req.jql(), req.description(), req.shared());
    }

    @PutMapping("/filters/{filterId}")
    SavedFilterResponse updateFilter(@PathVariable UUID filterId,
                                     @Valid @RequestBody FilterRequest req) {
        return filters.update(filterId, currentUser.requireId(),
                req.name(), req.jql(), req.description(), req.shared());
    }

    @DeleteMapping("/filters/{filterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFilter(@PathVariable UUID filterId) {
        filters.delete(filterId, currentUser.requireId());
    }

    /**
     * Runs a saved filter, under the *caller's* permissions rather than the owner's.
     */
    @GetMapping("/filters/{filterId}/results")
    SearchResults runFilter(@PathVariable UUID filterId,
                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
                            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        SavedFilterResponse filter = filters.get(filterId, currentUser.requireId());
        return search.search(filter.jql(), currentUser.requireId(), limit, offset);
    }

    record ValidateRequest(String jql) {
    }

    record FilterRequest(@NotBlank String name, @NotBlank String jql,
                         String description, boolean shared) {
    }
}
