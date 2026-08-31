package com.charlie.ikibaho.integration;

import com.charlie.ikibaho.integration.internal.security.HmacSigner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {
    private final HmacSigner signer = new HmacSigner();
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final String secret = "whsec_test";
    private final String payload = "{\"event\":\"issue.created\"}";

    @Test
    void producesTheDocumentedHeaderShape() {
        String header = signer.sign(payload, secret, now);

        assertThat(header).startsWith("t=" + now.getEpochSecond() + ",v1=");
        assertThat(header).matches("t=\\d+,v1=[0-9a-f]{64}");
    }

    @Test
    void roundTripsItsOwnSignature() {
        String header = signer.sign(payload, secret, now);

        assertThat(signer.verify(payload, secret, header, now, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        String header = signer.sign(payload, secret, now);

        assertThat(signer.verify("{\"event\":\"issue.deleted\"}", secret, header, now,
                Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void rejectsTheWrongSecret() {
        String header = signer.sign(payload, secret, now);

        assertThat(signer.verify(payload, "whsec_other", header, now, Duration.ofMinutes(5)))
                .isFalse();
    }

    @Test
    void rejectsAReplayOutsideTheToleranceWindow() {
        String header = signer.sign(payload, secret, now);

        // The signature is still cryptographically valid -- that is the point.
        // Binding the timestamp into it is what makes an old capture unusable.
        assertThat(signer.verify(payload, secret, header,
                now.plus(Duration.ofHours(1)), Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void bindsTheTimestampIntoTheSignatureRatherThanAlongsideIt() {
        String header = signer.sign(payload, secret, now);
        // Swapping in a fresh timestamp must invalidate it; if the body alone were
        // signed, this forgery would pass.
        String forged = header.replaceFirst("t=\\d+", "t=" + now.plusSeconds(60).getEpochSecond());

        assertThat(signer.verify(payload, secret, forged, now.plusSeconds(60),
                Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void rejectsAMalformedHeader() {
        assertThat(signer.verify(payload, secret, null, now, Duration.ofMinutes(5))).isFalse();
        assertThat(signer.verify(payload, secret, "garbage", now, Duration.ofMinutes(5))).isFalse();
        assertThat(signer.verify(payload, secret, "t=abc,v1=xyz", now, Duration.ofMinutes(5)))
                .isFalse();
    }

    @Test
    void generatesDistinctSecretsWithAnIdentifiablePrefix() {
        String first = signer.newSecret();
        String second = signer.newSecret();

        assertThat(first).startsWith("whsec_").hasSize(6 + 64);
        assertThat(first).isNotEqualTo(second);
    }
}
