package com.charlie.ikibaho.notification.internal.web;

import com.charlie.ikibaho.notification.internal.application.NotificationStream;
import com.charlie.ikibaho.platform.security.CurrentUser;
import com.charlie.ikibaho.platform.web.ApiVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(ApiVersion.V1 + "/notifications")
class NotificationStreamController {
    private final NotificationStream stream;
    private final CurrentUser currentUser;
    private final long timeoutMillis;

    NotificationStreamController(NotificationStream stream, CurrentUser currentUser,
                                 @Value("${ikibaho.notifications.sse-timeout:1800000}") long timeoutMillis) {
        this.stream = stream;
        this.currentUser = currentUser;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * The stream is per-authenticated-user and takes no id parameter.
     * <p>
     * Deliberate: an endpoint that accepted a user id would need a check that it
     * matches the caller, and that check is the kind that gets forgotten. Deriving
     * it from the token makes subscribing to someone else's notifications
     * unexpressible.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return stream.open(currentUser.requireId(), timeoutMillis);
    }
}
