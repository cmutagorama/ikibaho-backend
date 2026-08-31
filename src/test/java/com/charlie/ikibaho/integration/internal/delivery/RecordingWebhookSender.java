package com.charlie.ikibaho.integration.internal.delivery;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Substitutes for the HTTP hop.
 * <p>
 * Retry, backoff and signing are all our logic; testing them through a real
 * socket would add flakiness and prove nothing extra. What a real endpoint would
 * verify -- that the wire format is right -- is covered by HmacSignerTest.
 * <p>
 * Lives in the delivery package so it can implement WebhookSender, which is
 * public but nested inside `internal` -- reachable from a test, unreachable from
 * another module.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingWebhookSender {

    @Bean
    @Primary
    Recorder recordingWebhookSender() {
        return new Recorder();
    }

    public record Sent(String url, String secret, String eventType, String deliveryId,
                       String payload) {
    }

    public static class Recorder implements WebhookSender {
        private final List<Sent> sent = new CopyOnWriteArrayList<>();
        private volatile Integer failStatus;

        @Override
        public Result send(String url, String secret, String eventType, String deliveryId,
                           String payload) {
            sent.add(new Sent(url, secret, eventType, deliveryId, payload));

            Integer status = failStatus;
            return status == null ? Result.ok(200) : Result.failed(status, "HTTP " + status);
        }

        public List<Sent> sent() {
            return List.copyOf(sent);
        }

        public void reset() {
            sent.clear();
            failStatus = null;
        }

        /**
         * Makes every subsequent send fail, so backoff can be observed.
         */
        public void failWith(int status) {
            this.failStatus = status;
        }
    }
}
