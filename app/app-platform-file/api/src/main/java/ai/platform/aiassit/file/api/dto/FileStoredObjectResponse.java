package ai.platform.aiassit.file.api.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class FileStoredObjectResponse {

    private String bucket;

    private String objectKey;

    private Long size;

    private String etag;

    private Instant lastModified;

    private String contentType;
}
