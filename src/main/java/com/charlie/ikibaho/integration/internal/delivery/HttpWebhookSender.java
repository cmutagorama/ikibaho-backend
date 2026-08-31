package com.charlie.ikibaho.integration.internal.delivery;

import com.charlie.ikibaho.integration.internal.security.HmacSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

@Component
class HttpWebhookSender implements WebhookSender {
    private static final Logger log = LoggerFactory.getLogger(HttpWebhookSender.class);

    private final RestClient http;
    private final HmacSigner signer;
    private final boolean allowPrivateAddresses;

    /**
     * Builds its own client rather than injecting the shared RestClient.Builder.
     * <p>
     * Not only because Boot 4 no longer auto-configures that builder for a plain
     * webmvc starter -- the shared builder also carries whatever interceptors the
     * application has registered globally. An interceptor that propagates the
     * caller's Authorization header is a sensible thing to have and a catastrophe
     * here: it would attach this API's credentials to a request aimed at a
     * stranger's server. Outbound webhooks want a client that shares nothing.
     */
    HttpWebhookSender(HmacSigner signer,
                      @Value("${ikibaho.webhooks.timeout:PT10S}") Duration timeout,
                      @Value("${ikibaho.webhooks.allow-private-addresses:false}") boolean allowPrivate) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        this.http = RestClient.builder()
                .requestFactory(factory)
                // Redirects are not followed. A receiver that 302s to somewhere else
                // would let an attacker register a benign URL and bounce our signed
                // request at an internal address.
                .build();
        this.signer = signer;
        this.allowPrivateAddresses = allowPrivate;
    }

    @Override
    public Result send(String url, String secret, String eventType, String deliveryId, String payload) {
        try {
            if (!allowPrivateAddresses && resolvesToPrivateAddress(url)) {
                // Server-side request forgery: without this, anyone who can create a
                // webhook can point it at 169.254.169.254 or a database on the
                // private network and use this server as a probe.
                return Result.failed(null, "Refusing to deliver to a private address");
            }

            Instant now = Instant.now();
            var response = http.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HmacSigner.SIGNATURE_HEADER, signer.sign(payload, secret, now))
                    .header(HmacSigner.EVENT_HEADER, eventType)
                    .header(HmacSigner.DELIVERY_HEADER, deliveryId)
                    .header("User-Agent", "Ikibaho-Webhook/1.0")
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> true, (request, res) -> {
                        // No exception on any status; the caller decides what to do
                        // with a 4xx versus a 5xx.
                    })
                    .toBodilessEntity();

            int status = response.getStatusCode().value();
            return status >= 200 && status < 300
                    ? Result.ok(status)
                    : Result.failed(status, "HTTP " + status);

        } catch (Exception e) {
            log.debug("Webhook delivery to {} failed: {}", url, e.getMessage());
            return Result.failed(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Resolved at delivery time, not at registration.
     * <p>
     * A hostname that resolved to a public address when the webhook was created
     * can be repointed at a private one an hour later -- DNS rebinding. Checking
     * here is the only check that describes where the request is actually going.
     */
    private boolean resolvesToPrivateAddress(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return true;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException | IllegalArgumentException e) {
            // Cannot resolve it, so cannot vouch for it.
            return true;
        }
    }
}
