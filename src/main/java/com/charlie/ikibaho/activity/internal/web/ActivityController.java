package com.charlie.ikibaho.activity.internal.web;

import com.charlie.ikibaho.activity.internal.application.ActivityService;
import com.charlie.ikibaho.activity.internal.web.dto.ActivityEntry;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1)
@Validated
class ActivityController {
    private final ActivityService activity;
    private final CurrentUser currentUser;

    ActivityController(ActivityService activity, CurrentUser currentUser) {
        this.activity = activity;
        this.currentUser = currentUser;
    }

    @GetMapping("/issues/{issueId}/history")
    List<ActivityEntry> forIssue(@PathVariable UUID issueId,
                                 @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return activity.forIssue(issueId, currentUser.requireId(), limit);
    }

    @GetMapping("/projects/{projectId}/activity")
    List<ActivityEntry> forProject(@PathVariable UUID projectId,
                                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return activity.forProject(projectId, currentUser.requireId(), limit);
    }
}
