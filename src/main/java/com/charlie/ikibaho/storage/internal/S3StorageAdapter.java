package com.charlie.ikibaho.storage.internal;

import com.charlie.ikibaho.platform.error.ValidationException;
import com.charlie.ikibaho.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

@Service
public class S3StorageAdapter implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    private final S3Presigner presigner;
    private final S3Client client;
    private final StorageProperties properties;

    S3StorageAdapter(S3Presigner presigner, S3Client client, StorageProperties properties) {
        this.presigner = presigner;
        this.client = client;
        this.properties = properties;
    }

    /**
     * Quotes and control characters would let a filename forge a second header.
     */
    private static String sanitize(String filename) {
        String cleaned = filename.replaceAll("[\\p{Cntrl}\"\\\\]", "");
        if (cleaned.isBlank()) {
            throw new ValidationException("Filename is empty after sanitising");
        }
        return cleaned;
    }

    @Override
    public PresignedUrl presignUpload(String objectKey, String contentType, Duration ttl) {
        // Content-Type is part of what gets signed, so the client must send back
        // exactly this header. That is the point: it stops someone taking a URL
        // issued for a PNG and using it to store an HTML page that would then be
        // served from the same origin.
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(request -> request
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentType(contentType))
                .build());

        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public PresignedUrl presignDownload(String objectKey, String downloadFilename, Duration ttl) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request -> {
                    request.bucket(properties.bucket()).key(objectKey);
                    if (downloadFilename != null) {
                        // Forces a download with the original name rather than the
                        // opaque object key, and stops the browser rendering an
                        // uploaded HTML file inline.
                        request.responseContentDisposition(
                                "attachment; filename=\"" + sanitize(downloadFilename) + '"');
                    }
                })
                .build());

        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public Optional<StoredObject> head(String objectKey) {
        try {
            HeadObjectResponse response = client.headObject(
                    request -> request.bucket(properties.bucket()).key(objectKey));
            return Optional.of(new StoredObject(objectKey, response.contentLength(),
                    response.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // A 404 arrives as a generic S3Exception from some implementations.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(request -> request.bucket(properties.bucket()).key(objectKey));
        } catch (S3Exception e) {
            // Deleting an object is cleanup. Failing it must not fail the caller's
            // real work -- an orphaned object costs storage; a failed issue delete
            // costs the user their action.
            log.warn("Could not delete object {}: {}", objectKey, e.getMessage());
        }
    }
}
