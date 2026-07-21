package ai.platform.aiassit.conversation.dto.protocol;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RenderArtifactResponse {

    private String schemaVersion = "render-artifact.v1";

    private String codeRef;

    private String sessionCode;

    private String roundCode;

    private String artifactType;

    private String title;

    private String contentFormat;

    private Object content;

    private Map<String, Object> ext = new LinkedHashMap<>();
}
