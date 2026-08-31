package com.charlie.ikibaho.storage.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param endpoint        override for MinIO or LocalStack; null uses real AWS
 * @param pathStyleAccess MinIO needs path-style URLs; S3 proper prefers virtual-host
 * @param maxUploadBytes  refused before a URL is ever issued
 */
@ConfigurationProperties(prefix = "ikibaho.storage")
public record StorageProperties(
        @DefaultValue("ikibaho-attachments") String bucket,
        @DefaultValue("us-east-1") String region,
        String endpoint,
        String accessKey,
        String secretKey,
        @DefaultValue("false") boolean pathStyleAccess,
        @DefaultValue("PT15M") Duration uploadUrlTtl,
        @DefaultValue("PT5m") Duration downloadUrlTtl,
        @DefaultValue("26214400") long maxUploadBytes
) {
    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
