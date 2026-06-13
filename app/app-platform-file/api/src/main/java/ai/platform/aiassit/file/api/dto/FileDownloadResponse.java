package ai.platform.aiassit.file.api.dto;

import lombok.Data;

@Data
public class FileDownloadResponse {

    private String bucket;

    private String objectKey;

    private String contentType;

    private Long size;

    private byte[] content;
}
