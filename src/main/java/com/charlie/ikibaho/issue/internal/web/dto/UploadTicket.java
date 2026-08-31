package com.charlie.ikibaho.issue.internal.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Permission to upload one file, for a short while.
 * <p>
 * {@code contentType} is echoed back because it is part of the signature: the
 * client must send exactly this Content-Type header on the PUT or storage will
 * reject it.
 */
public record UploadTicket(
        UUID attachmentId,
        String uploadUrl,
        Instant expiresAt,
        String contentType
) {
}
