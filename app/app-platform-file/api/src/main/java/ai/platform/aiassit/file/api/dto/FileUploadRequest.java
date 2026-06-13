package ai.platform.aiassit.file.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class FileUploadRequest {

    private String bucket;

    private String objectKey;

    private String contentType;

    private byte[] content;

    private Map<String, String> metadata;
}
