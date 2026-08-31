package com.charlie.ikibaho.integration;

/**
 * The one and only time the secret is disclosed.
 * <p>
 * Same reasoning as an API key: it cannot be hashed, because signing needs the
 * original, so the mitigation is to show it once and expect the caller to store
 * it. A "reveal secret" endpoint would undo that entirely.
 */
public record WebhookCreated(WebhookResponse webhook, String secret) {
}