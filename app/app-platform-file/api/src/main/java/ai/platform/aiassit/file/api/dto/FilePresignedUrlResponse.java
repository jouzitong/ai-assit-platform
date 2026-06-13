package ai.platform.aiassit.file.api.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class FilePresignedUrlResponse {

    private String url;

    private String method;

    private Instant expireAt;
}
