package ai.platform.aiassit.data.virtualization.spi.knowledge;

import java.util.List;

public record KnowledgeDocumentQuery(List<String> documentCodes) {
    public KnowledgeDocumentQuery {
        documentCodes = documentCodes == null ? List.of() : List.copyOf(documentCodes);
    }
}
