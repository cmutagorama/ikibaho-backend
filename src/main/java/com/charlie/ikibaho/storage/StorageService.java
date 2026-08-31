package com.charlie.ikibaho.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Object storage, as the rest of the application sees it.
 * <p>
 * Deliberately narrow, and deliberately ignorant of what it is storing: no
 * mention of issues, attachments or users. Callers own the meaning of an object
 * key; this module owns getting bytes in and out of a bucket.
 * <p>
 * Nothing here streams content. Bytes go directly between the client and the
 * bucket via presigned URLs -- routing them through this process would put file
 * transfer on the threads serving the API, and make every upload's size a
 * question about heap.
 */
public interface StorageService {
    /**
     * A URL the client may PUT to, valid for {@code ttl}.
     */
    PresignedUrl presignUpload(String objectKey, String contentType, Duration ttl);

    /**
     * A URL the client may GET from.
     *
     * @param downloadFilename the name the browser should save it as, or null to
     *                         let the browser decide
     */
    PresignedUrl presignDownload(String objectKey, String downloadFilename, Duration ttl);

    /**
     * The stored object's real size, or empty if it is not there.
     * <p>
     * The one call that talks to storage synchronously, because it is how the
     * server learns whether an upload it never saw actually happened -- and how
     * big it really was, as opposed to what the client claimed.
     */
    Optional<StoredObject> head(String objectKey);

    void delete(String objectKey);

    record PresignedUrl(String url, Instant expiresAt) {
    }

    record StoredObject(String objectKey, long sizeBytes, String contentType) {
    }
}
