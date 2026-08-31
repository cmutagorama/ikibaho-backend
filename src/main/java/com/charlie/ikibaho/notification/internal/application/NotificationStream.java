package com.charlie.ikibaho.notification.internal.application;

import com.charlie.ikibaho.notification.internal.web.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live notifications over Server-Sent Events.
 * <p>
 * SSE rather than WebSockets: this traffic is one-directional and low volume, and
 * SSE is plain HTTP -- it reconnects on its own, works through ordinary proxies,
 * and needs no second protocol in the stack.
 * <p>
 * Connections are held per instance, in memory. That means a notification is
 * delivered live only to sockets attached to the instance that produced it.
 * Acceptable because the badge count is authoritative and polled on load; a
 * missed push costs a moment's latency, not a lost notification. Making it
 * cluster-wide means a shared bus, which is a real cost for that small a gain.
 */
@Component
public class NotificationStream {
    private static final Logger log = LoggerFactory.getLogger(NotificationStream.class);

    /**
     * One user may have several tabs open, each its own emitter.
     */
    private final Map<UUID, List<SseEmitter>> connections = new ConcurrentHashMap<>();

    public SseEmitter open(UUID userId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        connections.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        // All three, not just onCompletion: a client that vanishes mid-stream fires
        // onError, and a long-lived connection that ages out fires onTimeout. Miss
        // either and the map grows forever.
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        try {
            // An immediate event so the client knows the stream is live rather than
            // merely accepted -- and so any proxy in between flushes its buffer.
            emitter.send(SseEmitter.event().name("connected").data(Map.of("userId", userId)));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /**
     * Called by the dispatcher after a notification is saved.
     */
    public void push(UUID userId, NotificationResponse notification) {
        List<SseEmitter> emitters = connections.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (IOException | IllegalStateException e) {
                // The client went away. Not worth logging at anything above debug --
                // closing a tab is normal.
                log.debug("Dropping dead SSE connection for {}", userId);
                remove(userId, emitter);
            }
        }
    }

    /**
     * Keeps connections alive through proxies that cut idle streams.
     * <p>
     * Deliberately NOT @SchedulerLock'd. Every instance must heartbeat its own
     * sockets; a cluster-wide lock would mean two of three instances silently
     * letting their connections die.
     */
    @Scheduled(fixedDelayString = "${ikibaho.notifications.sse-heartbeat:25000}")
    void heartbeat() {
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    public int connectionCount() {
        return connections.values().stream().mapToInt(List::size).sum();
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = connections.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        // Prune the empty list, or a busy day leaves an entry per user forever.
        connections.computeIfPresent(userId, (key, value) -> value.isEmpty() ? null : value);
    }
}
