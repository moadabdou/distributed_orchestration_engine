package com.doe.manager.workflow;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**

 * Service to interact with MinIO storage.
 */
@Service
public class MinioService {

    private static final Logger LOG = LoggerFactory.getLogger(MinioService.class);

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;

    private MinioClient minioClient;

    public MinioService(
            @Value("${doe.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${doe.storage.minio.access-key:admin}") String accessKey,
            @Value("${doe.storage.minio.secret-key:password123}") String secretKey,
            @Value("${doe.storage.minio.bucket:fernos-storage}") String bucket) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    @PostConstruct
    public void init() {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            LOG.info("MinioClient initialized with endpoint: {}", endpoint);
        } catch (Exception e) {
            LOG.error("Failed to initialize MinioClient", e);
        }
    }

    /**
     * Deletes an object from MinIO.
     *
     * @param objectName the name/path of the object to delete
     */
    public void deleteObject(String objectName) {
        if (minioClient == null) {
            LOG.warn("MinioClient not initialized, skipping deletion of {}", objectName);
            return;
        }

        try {
            LOG.info("Deleting object from MinIO: bucket={}, object={}", bucket, objectName);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            LOG.error("Failed to delete object from MinIO: {}", objectName, e);
        }
    }
}
