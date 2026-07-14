package ai.platform.aiassit.data.virtualization.spi.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

public record KnowledgeDocumentCommand(
        String knowledgeBaseCode,
        String documentCode,
        String documentName,
        String content,
        boolean updateAllowed,
        Map<String, Object> metadata
) {
    public KnowledgeDocumentCommand {
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }
}
