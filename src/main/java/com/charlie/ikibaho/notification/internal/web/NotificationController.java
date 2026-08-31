package com.charlie.ikibaho.notification.internal.web;

import com.charlie.ikibaho.notification.internal.application.NotificationService;
import com.charlie.ikibaho.notification.internal.web.dto.NotificationResponse;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/notifications")
@Validated
class NotificationController {
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<NotificationResponse> inbox(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return notifications.inbox(currentUser.requireId(), unreadOnly, limit);
    }

    /** Drives the badge in the header, so it stays cheap and separate from the list. */
    @GetMapping("/unread-count")
    Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(currentUser.requireId()));
    }

    @PostMapping("/{notificationId}/read")
    NotificationResponse markRead(@PathVariable UUID notificationId) {
        return notifications.markRead(notificationId, currentUser.requireId());
    }

    @PostMapping("/read-all")
    Map<String, Integer> markAllRead() {
        return Map.of("updated", notifications.markAllRead(currentUser.requireId()));
    }
}
