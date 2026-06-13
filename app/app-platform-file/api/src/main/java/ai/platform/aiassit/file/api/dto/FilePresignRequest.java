package ai.platform.aiassit.file.api.dto;

import lombok.Data;

@Data
public class FilePresignRequest {

    private String bucket;

    private String objectKey;

    private Long expireSeconds;
}
