package ai.platform.aiassit.file.api.dto;

import lombok.Data;

@Data
public class FileObjectRequest {

    private String bucket;

    private String objectKey;
}
