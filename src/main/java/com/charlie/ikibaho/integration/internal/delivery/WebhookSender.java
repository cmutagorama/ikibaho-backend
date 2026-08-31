package com.charlie.ikibaho.integration.internal.delivery;

/**
 * The HTTP hop, behind an interface so tests can substitute it.
 * <p>
 * Delivery is the one part of this module that talks to a machine we do not
 * control, and testing retry and backoff against real sockets would make the
 * suite slow and flaky for no extra confidence.
 */
public interface WebhookSender {
    Result send(String url, String secret, String eventType, String deliveryId, String payload);

    /**
     * @param status null when the request never got a response at all -- DNS
     *               failure, refused connection, timeout
     */
    record Result(boolean success, Integer status, String error) {

        static Result ok(int status) {
            return new Result(true, status, null);
        }

        static Result failed(Integer status, String error) {
            return new Result(false, status, error);
        }
    }
}
