package com.charlie.ikibaho.support;

import com.charlie.ikibaho.storage.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration(proxyBeanMethods = false)
public class InMemoryStorage {
    @Bean
    @Primary
    Fake inMemoryStorage() {
        return new Fake();
    }

    public static class Fake implements StorageService {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private volatile String lastKey;

        public void reset() {
            objects.clear();
            lastKey = null;
        }

        /**
         * Simulates the client's PUT, which the server never sees in production.
         */
        public void put(String objectKey, long sizeBytes, String contentType) {
            objects.put(objectKey, new StoredObject(objectKey, sizeBytes, contentType));
        }

        public String lastKey() {
            return lastKey;
        }

        @Override
        public PresignedUrl presignUpload(String objectKey, String contentType, Duration ttl) {
            lastKey = objectKey;
            return new PresignedUrl("https://storage.test/" + objectKey + "?upload",
                    Instant.now().plus(ttl));
        }

        @Override
        public PresignedUrl presignDownload(String objectKey, String downloadFilename, Duration ttl) {
            return new PresignedUrl("https://storage.test/" + objectKey + "?download",
                    Instant.now().plus(ttl));
        }

        @Override
        public Optional<StoredObject> head(String objectKey) {
            return Optional.ofNullable(objects.get(objectKey));
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }
}
