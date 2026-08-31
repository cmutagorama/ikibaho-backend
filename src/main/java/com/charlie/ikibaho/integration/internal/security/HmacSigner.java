package com.charlie.ikibaho.integration.internal.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Signs webhook payloads so a receiver can tell a real delivery from a forgery.
 * <p>
 * The signed string is {@code <timestamp>.<body>}, not the body alone. Signing
 * only the body makes every delivery replayable forever: an attacker who
 * captures one valid request can resend it unchanged, and the signature still
 * checks out. Binding a timestamp into the signature lets the receiver reject
 * anything older than its tolerance.
 * <p>
 * Header format, one line, deliberately like Stripe's -- receivers already know
 * how to parse it:
 * <pre>X-Ikibaho-Signature: t=1735689600,v1=9f86d081...</pre>
 */
@Component
public class HmacSigner {
    public static final String SIGNATURE_HEADER = "X-Ikibaho-Signature";
    public static final String EVENT_HEADER = "X-Ikibaho-Event";
    public static final String DELIVERY_HEADER = "X-Ikibaho-Delivery";

    private static final String ALGORITHM = "HmacSHA256";

    public String sign(String payload, String secret, Instant timestamp) {
        long seconds = timestamp.getEpochSecond();
        String signature = hexHmac(seconds + "." + payload, secret);
        return "t=" + seconds + ",v1=" + signature;
    }

    /**
     * Verifies a header this class produced.
     * <p>
     * Provided so the test suite proves the scheme round-trips, and so anyone
     * writing a receiver has a reference implementation to copy rather than
     * guessing at the byte layout.
     */
    public boolean verify(String payload, String secret, String header, Instant now,
                          java.time.Duration tolerance) {
        if (header == null) {
            return false;
        }
        Long timestamp = null;
        String provided = null;
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if (pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (pair[0].equals("v1")) {
                provided = pair[1];
            }
        }
        if (timestamp == null || provided == null) {
            return false;
        }
        // Outside the window, a correct signature is still a replay.
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.getSeconds()) {
            return false;
        }

        String expected = hexHmac(timestamp + "." + payload, secret);

        // Constant-time. String.equals returns as soon as two bytes differ, and
        // that timing difference is enough to recover a signature byte by byte.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A fresh secret for a new webhook. 256 bits, matching the HMAC's own strength.
     */
    public String newSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private String hexHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is required of every JVM; if it is missing, the deployment
            // is broken in a way no caller can handle.
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
